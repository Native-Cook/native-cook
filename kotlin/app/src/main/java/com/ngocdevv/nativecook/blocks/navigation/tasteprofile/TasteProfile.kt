package com.ngocdevv.nativecook.blocks.navigation.tasteprofile

import android.app.Activity
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion

/**
 * A measured profile screen: a city filter that turns the stats and photos into a split-flap departure
 * board, a 3D cover-flow photo strip and a held stat tile that grows into a bar chart.
 * Visual animation lives in the draw phase; Compose state changes only at semantic boundaries.
 */
@Composable
fun TasteProfile(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reducedMotion = rememberReducedMotion()
    val accessibility = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var touchExploration by remember(accessibility) { mutableStateOf(accessibility.isTouchExplorationEnabled) }
    val looping = autoPlay && !reducedMotion && !touchExploration
    val controller = remember { TasteProfileController(looping, tasteProfileNowSeconds()) }
    val assets = remember(context) { TasteProfileAssets(context.applicationContext) }
    val renderer = remember(assets) { TasteProfileRenderer(assets) }
    val board = remember(assets) { TasteProfileBoardPainter(assets) }
    val chart = remember(assets) { TasteProfileChartPainter(assets, renderer, board) }
    var revision by remember { mutableIntStateOf(0) }
    var selectedCity by remember { mutableIntStateOf(0) }
    var following by remember { mutableStateOf(false) }

    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        accessibility.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility.removeTouchExplorationStateChangeListener(listener) }
    }
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controllerInsets = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controllerInsets?.isAppearanceLightStatusBars
        val previousNavigation = controllerInsets?.isAppearanceLightNavigationBars
        controllerInsets?.isAppearanceLightStatusBars = true
        controllerInsets?.isAppearanceLightNavigationBars = true
        onDispose {
            if (previousStatus != null) controllerInsets.isAppearanceLightStatusBars = previousStatus
            if (previousNavigation != null) controllerInsets.isAppearanceLightNavigationBars = previousNavigation
        }
    }
    DisposableEffect(lifecycleOwner, controller) {
        controller.setLifecycleActive(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), tasteProfileNowSeconds())
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.setLifecycleActive(true, tasteProfileNowSeconds())
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> controller.setLifecycleActive(false, tasteProfileNowSeconds())
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view, controller) {
        controller.onSelectionHaptic = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
        controller.onImpactHaptic = { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
        onDispose {
            controller.onSelectionHaptic = {}
            controller.onImpactHaptic = {}
        }
    }
    LaunchedEffect(controller, looping, reducedMotion) {
        controller.configure(looping, reducedMotion, tasteProfileNowSeconds())
        while (true) {
            withFrameNanos { frameNanos ->
                controller.tick(frameNanos / 1_000_000_000.0)
                val city = controller.selectedCity
                if (selectedCity != city) selectedCity = city
                if (following != controller.following) following = controller.following
                revision++
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val unit = widthPx / REF_W
        val topInset = WindowInsets.safeDrawing.getTop(density).toFloat()
        val y0 = topInset - REF_INSET * unit
        val fontScale = maxWidth.value / REF_PT
        val geo = TasteProfileGeo(unit, y0, fontScale, density.density)
        controller.setGeometry(
            cell = (TasteProfileBoxes.strip.w / 4f * unit).toDouble(),
            unit = unit.toDouble(),
            tileCardX = (0 until 4).map { chart.rectOf(it).left.toDouble() },
        )
        val onRevision = { revision++; Unit }

        // The screen blurs behind a held tile (API 31+; older devices keep the veil alone).
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (revision >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val radius = 3.5f * controller.frame.hold.e.toFloat() * density.density
                        renderEffect = if (radius > 0.01f) BlurEffect(radius, radius, TileMode.Clamp) else null
                    }
                },
        ) {
            if (revision >= 0) drawTasteProfile(renderer, board, geo, controller.frame)
        }
        Canvas(Modifier.fillMaxSize()) {
            if (revision >= 0) {
                drawContext.canvas.nativeCanvas.let { chart.draw(it, size.width, size.height, geo, controller.frame) }
            }
        }

        TasteProfileBoxes.chips.forEachIndexed { index, chip ->
            TasteProfileChipTarget(
                index = index,
                selected = selectedCity == index,
                x = (chip.x * unit).toDp(density.density),
                y = (y0 + (chip.y - 18f) * unit).toDp(density.density),
                width = (chip.w * unit).toDp(density.density),
                height = ((chip.h + 36f) * unit).toDp(density.density),
                controller = controller,
                onRevision = onRevision,
            )
        }

        val follow = TasteProfileBoxes.follow
        TasteProfileFollowTarget(
            following = following,
            x = ((follow.x - 12f) * unit).toDp(density.density),
            y = (y0 + (follow.y - 18f) * unit).toDp(density.density),
            width = ((follow.w + 24f) * unit).toDp(density.density),
            height = ((follow.h + 36f) * unit).toDp(density.density),
            controller = controller,
            reducedMotion = reducedMotion,
            onRevision = onRevision,
        )

        val strip = TasteProfileBoxes.strip
        TasteProfileStripTarget(
            x = strip.x * unit,
            y = y0 + strip.y * unit,
            width = strip.w * unit,
            height = strip.h * unit,
            controller = controller,
            onRevision = onRevision,
        )
        TasteProfileBoxes.tiles.forEachIndexed { index, tileX ->
            TasteProfileTileTarget(
                index = index,
                label = "${STATS[selectedCity][index]} ${TasteProfileTileLabels[index].value}",
                x = tileX * unit,
                y = y0 + TasteProfileBoxes.tileY * unit,
                width = TasteProfileBoxes.tileW * unit,
                height = TasteProfileBoxes.tileH * unit,
                controller = controller,
                onRevision = onRevision,
            )
        }
    }
}

@Composable
private fun TasteProfileStripTarget(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    controller: TasteProfileController,
    onRevision: () -> Unit,
) {
    val density = LocalDensity.current
    Box(
        Modifier
            .offset(x = x.toDp(density.density), y = y.toDp(density.density))
            .size(width.toDp(density.density), height.toDp(density.density))
            .semantics { contentDescription = "Photos" }
            .pointerInput(controller) {
                val slopX = 8.dp.toPx()
                val failY = 12.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    controller.stripBegin(tasteProfileNowSeconds())
                    onRevision()
                    var active = false
                    var failed = false
                    var lastMove = down.uptimeMillis
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val now = tasteProfileNowSeconds()
                        if (!failed) {
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!active) {
                                if (abs(dy) > failY) {
                                    failed = true
                                    controller.stripFinalize(now)
                                } else if (abs(dx) > slopX) {
                                    active = true
                                    controller.stripStart(now)
                                }
                            }
                            if (active) {
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (change.positionChanged()) lastMove = change.uptimeMillis
                                controller.stripMove(dx.toDouble(), now)
                            }
                        }
                        onRevision()
                        if (change.changedToUp() || !change.pressed) {
                            if (!failed) {
                                val rested = change.uptimeMillis - lastMove > 80
                                val velocity = if (active && !rested) tracker.calculateVelocity().x.toDouble() else 0.0
                                if (active) controller.stripEnd(velocity, now)
                                controller.stripFinalize(now)
                            }
                            break
                        }
                    }
                    onRevision()
                }
            },
    )
}

@Composable
private fun TasteProfileTileTarget(
    index: Int,
    label: String,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    controller: TasteProfileController,
    onRevision: () -> Unit,
) {
    val density = LocalDensity.current
    Box(
        Modifier
            .offset(x = x.toDp(density.density), y = y.toDp(density.density))
            .size(width.toDp(density.density), height.toDp(density.density))
            .semantics { contentDescription = "$label, hold to see the last six months" }
            .pointerInput(controller, index) {
                val slop = 10.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val originX = x
                    controller.tileDown(index, tasteProfileNowSeconds())
                    onRevision()
                    var failed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val now = tasteProfileNowSeconds()
                        if (!failed) {
                            if (controller.tileGrown) {
                                controller.tileMove((originX + change.position.x).toDouble(), index, now)
                            } else if ((change.position - down.position).getDistance() > slop) {
                                failed = true
                                controller.tileEnd(now)
                            }
                        }
                        onRevision()
                        if (change.changedToUp() || !change.pressed) break
                    }
                    if (!failed) controller.tileEnd(tasteProfileNowSeconds())
                    onRevision()
                }
            },
    )
}

@Composable
private fun TasteProfileChipTarget(
    index: Int,
    selected: Boolean,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    controller: TasteProfileController,
    onRevision: () -> Unit,
) {
    val commit = {
        val now = tasteProfileNowSeconds()
        controller.chipRelease(now)
        controller.chipTap(index, now)
        onRevision()
    }
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(width, height)
            .semantics {
                role = Role.Tab
                contentDescription = CITIES[index]
                this.selected = selected
                onClick(label = "Select ${CITIES[index]}") {
                    controller.chipDown(index, tasteProfileNowSeconds())
                    commit()
                    true
                }
            }
            .pointerInput(controller, index) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    controller.chipDown(index, tasteProfileNowSeconds())
                    onRevision()
                    val up = waitForUpOrCancellation()
                    if (up != null && up.position.inside(size.width.toFloat(), size.height.toFloat())) {
                        commit()
                    } else {
                        controller.chipCancel(tasteProfileNowSeconds())
                        onRevision()
                    }
                }
            },
    )
}

@Composable
private fun TasteProfileFollowTarget(
    following: Boolean,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    controller: TasteProfileController,
    reducedMotion: Boolean,
    onRevision: () -> Unit,
) {
    val toggle = {
        controller.toggleFollow(tasteProfileNowSeconds(), reducedMotion)
        onRevision()
    }
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(width, height)
            .semantics {
                role = Role.Button
                contentDescription = if (following) "Following" else "Follow"
                selected = following
                onClick(label = if (following) "Unfollow" else "Follow") {
                    toggle()
                    true
                }
            }
            .pointerInput(controller, reducedMotion) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    controller.followDown(tasteProfileNowSeconds(), reducedMotion)
                    onRevision()
                    val up = waitForUpOrCancellation()
                    controller.followRelease(tasteProfileNowSeconds())
                    if (up != null && up.position.inside(size.width.toFloat(), size.height.toFloat())) toggle() else onRevision()
                }
            },
    )
}

private fun Float.toDp(density: Float): Dp = (this / density).dp

private fun Offset.inside(width: Float, height: Float): Boolean = x in 0f..width && y in 0f..height
