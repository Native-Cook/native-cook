package com.ngocdevv.nativecook.blocks.sheets.savedplacesmap

import android.app.Activity
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

const val LOOP = 7.5

/**
 * A saved café map: the OSM map under a progressive blur pitches into 3D as the sheet is pulled, the cafés stand up
 * as pins with light beams and long shadows, loose markers fly into count bubbles. The painter owns static
 * resources; the Canvases observe only a draw revision each frame. Reduce Motion and the screen reader are
 * followed live.
 */
@Composable
fun SavedPlacesMap(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val live = rememberLiveAccessibility()
    val reducedMotion = live.reduceMotion
    val assets = remember(context) { SavedPlacesMapAssets(context) }
    val painter = remember(assets) { SavedPlacesMapPainter(assets) }
    val controller = remember(autoPlay) { SavedPlacesMapController(autoPlay, reducedMotion, live.calm) }
    val motionEvents = remember(controller) { Channel<Unit>(Channel.CONFLATED) }
    var drawRevision by remember { mutableLongStateOf(0L) }
    var lifecycleActive by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    // Reduce Motion / screen reader switched while the screen is open.
    SideEffect {
        controller.configure(reducedMotion, live.calm, System.nanoTime())
        motionEvents.trySend(Unit)
        drawRevision++
    }

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = insetsController?.isAppearanceLightStatusBars
        val previousNavigation = insetsController?.isAppearanceLightNavigationBars
        insetsController?.isAppearanceLightStatusBars = true
        insetsController?.isAppearanceLightNavigationBars = true
        onDispose {
            if (previousStatus != null) {
                insetsController.isAppearanceLightStatusBars = previousStatus
            }
            if (previousNavigation != null) {
                insetsController.isAppearanceLightNavigationBars = previousNavigation
            }
        }
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    controller.setLifecycleActive(true, System.nanoTime())
                    lifecycleActive = true
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    controller.setLifecycleActive(false, System.nanoTime())
                    lifecycleActive = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(controller, lifecycleActive) {
        if (!lifecycleActive) {
            return@LaunchedEffect
        }
        controller.setLifecycleActive(true, System.nanoTime())
        while (currentCoroutineContext().isActive) {
            val waitMillis = controller.nextWakeDelayMillis(System.nanoTime())
            if (waitMillis == null) {
                motionEvents.receive()
                continue
            }
            if (waitMillis > 16L) {
                withTimeoutOrNull(waitMillis.coerceAtLeast(1L)) { motionEvents.receive() }
                continue
            }
            withFrameNanos { now ->
                if (controller.tick(now)) {
                    drawRevision++
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFDDEEF8)),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val unit = widthPx / SavedPlacesMapTheme.REF_W
        val blurHeight = with(density) { (SavedPlacesMapTheme.TopBlur.h * unit).toDp() }
        val fadeHeight = with(density) { (SavedPlacesMapTheme.TopBlur.fadeH * unit).toDp() }
        val actionHaptic: (Int) -> Unit = { haptic ->
            performSavedPlacesHaptic(view, haptic)
            motionEvents.trySend(Unit)
            drawRevision++
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(controller, unit, density.density) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        tracker.resetTracking()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        controller.onDown(down.position.x, down.position.y, unit, System.nanoTime())
                        motionEvents.trySend(Unit)
                        drawRevision++
                        var completed = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { candidate -> candidate.id == down.id }
                                    ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    val haptic = controller.onUp(
                                        x = change.position.x,
                                        y = change.position.y,
                                        velocityY = tracker.calculateVelocity().y,
                                        unit = unit,
                                        density = density.density,
                                        nowNanos = System.nanoTime(),
                                    )
                                    performSavedPlacesHaptic(view, haptic)
                                    motionEvents.trySend(Unit)
                                    drawRevision++
                                    completed = true
                                    break
                                }
                                if (controller.onMove(change.position.x, change.position.y, unit, density.density, System.nanoTime())) {
                                    motionEvents.trySend(Unit)
                                    change.consume()
                                    drawRevision++
                                }
                            }
                        } finally {
                            if (!completed) {
                                controller.onCancel(System.nanoTime())
                                motionEvents.trySend(Unit)
                                drawRevision++
                            }
                        }
                    }
                },
        ) {
            // The scene under the blur: sky, pitched map, haze, pins.
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        // Reading the scalar here invalidates the layer without composing the screen.
                        alpha = 1f + drawRevision * 0f
                    },
            ) {
                if (drawRevision >= 0L) {
                    painter.drawScene(drawContext.canvas.nativeCanvas, size.width, controller.frame, reducedMotion)
                }
            }

            // The pin's progressive blur: a blurred copy of the top of the scene, masked by a vertical gradient.
            val topBlur = SavedPlacesMapTheme.TopBlur
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(blurHeight)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                topBlur.solid / topBlur.h to Color.Black,
                                1f to Color.Transparent,
                                startY = 0f,
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // RenderEffect blur needs API 31; below it the white veil alone stays.
                    val sigma = topBlur.sigma * unit
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .graphicsLayer {
                                renderEffect = BlurEffect(sigma, sigma, TileMode.Clamp)
                                alpha = 1f + drawRevision * 0f
                            },
                    ) {
                        if (drawRevision >= 0L) {
                            painter.drawScene(drawContext.canvas.nativeCanvas, size.width, controller.frame, reducedMotion)
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = topBlur.tint)),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .background(
                        Brush.verticalGradient(
                            *Array(topBlur.fadeAlphas.size) { i ->
                                topBlur.fadeStops[i] to Color.White.copy(alpha = topBlur.fadeAlphas[i])
                            },
                        ),
                    ),
            )

            // Back button, floating controls and the sheet.
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        alpha = 1f + drawRevision * 0f
                    },
            ) {
                if (drawRevision >= 0L) {
                    painter.drawFront(drawContext.canvas.nativeCanvas, size.width, size.height, controller.frame, reducedMotion)
                }
            }

            if (live.serviceEnabled || live.screenReader) {
                SavedPlacesMapAccessibilityLayer(
                    controller = controller,
                    unit = unit,
                    reducedMotion = reducedMotion,
                    revision = { drawRevision },
                    onAction = actionHaptic,
                )
            }
        }
    }
}

private fun performSavedPlacesHaptic(view: android.view.View, haptic: Int) {
    when (haptic) {
        SavedPlacesMapController.HAPTIC_DETENT -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        SavedPlacesMapController.HAPTIC_VISIT_ON -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        SavedPlacesMapController.HAPTIC_SELECT -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

/**
 * The screen as elements for TalkBack: cafés riding their pins, then the sheet's layer. Transparent nodes sized and
 * placed from the same projection the painter uses; they exist only while an accessibility service is on.
 */
@Composable
private fun SavedPlacesMapAccessibilityLayer(
    controller: SavedPlacesMapController,
    unit: Float,
    reducedMotion: Boolean,
    revision: () -> Long,
    onAction: (Int) -> Unit,
) {
    val copy = SavedPlacesMapTheme.Copy
    val expanded by remember(controller) { derivedStateOf { revision() >= 0L && controller.frame.sheet > 0.5 } }
    val visited by remember(controller) { derivedStateOf { revision() >= 0L && controller.frame.fill > 0.5 } }
    val token = SavedPlacesMapTheme.MarkerToken
    val raisedTop = SavedPlacesMapTheme.Sheet.top - SavedPlacesMapMotion.RISE * 3f

    val visitedIndex = SavedPlacesMapTheme.markers.indexOfFirst { marker -> marker.key == SavedPlacesMapTheme.VISITED_KEY }

    Box(Modifier.fillMaxSize()) {
        SavedPlacesMapTheme.markers.forEachIndexed { index, marker ->
            val cx = marker.x - token.discDx
            val cy = marker.baseline - token.discDy
            val covered = expanded && cy + token.disc / 2f > raisedTop
            if (!covered) {
                val rank = SavedPlacesMapTheme.depthRank[marker.key] ?: 0
                AccessibilityNode(
                    bounds = {
                        revision()
                        val tilt = if (reducedMotion) 0.0 else SavedPlacesMapMotion.tiltOf(controller.frame.sheet)
                        val disc = SavedPlacesMapMotion.discAt(cx.toDouble(), cy.toDouble(), tilt, rank, token.disc / 2.0)
                        val m = disc.m.toFloat()
                        val x0 = cx - token.disc / 2f
                        val y0 = cy - token.disc / 2f
                        val w = marker.x + token.hitW - x0
                        floatArrayOf(
                            (cx + (x0 - cx) * m + (disc.x.toFloat() - cx)) * unit,
                            (cy + (y0 - cy) * m + (disc.y.toFloat() - cy)) * unit,
                            w * m * unit,
                            token.disc * m * unit,
                        )
                    },
                ) {
                    role = Role.Button
                    contentDescription = "${marker.name}, ${marker.sub}, ${marker.tag}" +
                        if (index == visitedIndex && visited) ", ${copy.a11yVisited}" else ""
                    onClick(label = "highlight it on the map") {
                        onAction(controller.selectMarker(index, System.nanoTime()))
                        true
                    }
                }
            }
        }

        fun sheetNode(x: Float, y: Float, w: Float, h: Float): () -> FloatArray = {
            revision()
            val off = SavedPlacesMapMotion.RISE * 3f * controller.frame.sheet.toFloat()
            floatArrayOf(x * unit, (y - off) * unit, w * unit, h * unit)
        }

        AccessibilityNode(
            bounds = sheetNode(0f, SavedPlacesMapTheme.Sheet.top, SavedPlacesMapTheme.REF_W, SavedPlacesMapTheme.Tiles.y - SavedPlacesMapTheme.Sheet.top),
        ) {
            contentDescription = "${copy.title}, ${copy.places}, ${copy.saves}, ${copy.views}, ${copy.a11yBy} ${copy.handle}"
            stateDescription = if (expanded) copy.a11yExpanded else copy.a11yCollapsed
            progressBarRangeInfo = ProgressBarRangeInfo(if (expanded) 1f else 0f, 0f..1f, 0)
            setProgress { value ->
                controller.setSheetDetent(open = value > 0.5f, nowNanos = System.nanoTime())
                onAction(SavedPlacesMapController.HAPTIC_DETENT)
                true
            }
            onClick(label = "expand or collapse the list") {
                controller.setSheetDetent(open = !expanded, nowNanos = System.nanoTime())
                onAction(SavedPlacesMapController.HAPTIC_DETENT)
                true
            }
            customActions = listOf(
                CustomAccessibilityAction("Expand the list") {
                    controller.setSheetDetent(open = true, nowNanos = System.nanoTime())
                    onAction(SavedPlacesMapController.HAPTIC_DETENT)
                    true
                },
                CustomAccessibilityAction("Collapse the list") {
                    controller.setSheetDetent(open = false, nowNanos = System.nanoTime())
                    onAction(SavedPlacesMapController.HAPTIC_DETENT)
                    true
                },
            )
        }

        AccessibilityNode(
            bounds = sheetNode(SavedPlacesMapTheme.Tiles.xs[0], SavedPlacesMapTheme.Tiles.y, SavedPlacesMapTheme.Tiles.w, SavedPlacesMapTheme.Tiles.h),
        ) {
            role = Role.Button
            contentDescription = copy.visited
            stateDescription = "${if (visited) copy.visitedNext else copy.visitedNum} ${copy.a11yOf} ${copy.visitedDen.drop(1)}"
            onClick(label = "mark ${copy.listTitle} visited") {
                onAction(controller.toggleVisited(System.nanoTime()))
                true
            }
        }
        listOf(copy.save, copy.share, copy.similar).forEachIndexed { index, label ->
            AccessibilityNode(
                bounds = sheetNode(SavedPlacesMapTheme.Tiles.xs[index + 1], SavedPlacesMapTheme.Tiles.y, SavedPlacesMapTheme.Tiles.w, SavedPlacesMapTheme.Tiles.h),
            ) {
                contentDescription = label
            }
        }
        AccessibilityNode(
            bounds = sheetNode(SavedPlacesMapTheme.Layout.cardX, SavedPlacesMapTheme.Layout.cardY, SavedPlacesMapTheme.Layout.cardW, SavedPlacesMapTheme.ListToken.cardH),
        ) {
            contentDescription = "${copy.listTitle}, ${copy.listSub}"
        }
        val box = SavedPlacesMapTheme.Layout
        AccessibilityNode(
            bounds = sheetNode(box.checkboxX + box.checkboxD / 2f - 66f, box.checkboxY + box.checkboxD / 2f - 66f, 132f, 132f),
        ) {
            role = Role.Checkbox
            contentDescription = copy.a11yVisitedBox
            toggleableState = if (visited) ToggleableState.On else ToggleableState.Off
            onClick(label = null) {
                onAction(controller.toggleVisited(System.nanoTime()))
                true
            }
        }
        if (expanded) {
            SavedPlacesMapTheme.moreCards.forEachIndexed { index, card ->
                AccessibilityNode(
                    bounds = sheetNode(
                        SavedPlacesMapTheme.Layout.cardX,
                        SavedPlacesMapTheme.Layout.cardY + (SavedPlacesMapTheme.ListToken.cardH + SavedPlacesMapTheme.ListToken.gap) * (index + 1),
                        SavedPlacesMapTheme.Layout.cardW,
                        SavedPlacesMapTheme.ListToken.cardH,
                    ),
                ) {
                    contentDescription = "${card.title}, ${card.sub}"
                }
            }
        }
    }
}

/** A transparent node whose bounds (x, y, width, height in px) are read in the layout phase. */
@Composable
private fun AccessibilityNode(
    bounds: () -> FloatArray,
    semanticsBlock: SemanticsPropertyReceiver.() -> Unit,
) {
    Box(
        Modifier
            .offset {
                val b = bounds()
                IntOffset(b[0].roundToInt(), b[1].roundToInt())
            }
            .layout { measurable, _ ->
                val b = bounds()
                val width = b[2].roundToInt().coerceAtLeast(1)
                val height = b[3].roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .semantics(properties = semanticsBlock),
    )
}
