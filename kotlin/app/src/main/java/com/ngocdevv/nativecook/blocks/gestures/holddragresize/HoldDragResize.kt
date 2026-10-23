package com.ngocdevv.nativecook.blocks.gestures.holddragresize

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@Composable
fun HoldDragResize(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val reducedMotion = rememberReducedMotion()
    val safeDrawing = WindowInsets.safeDrawing
    val topInset = safeDrawing.getTop(density) / density.density.toDouble()
    val bottomInset = safeDrawing.getBottom(density) / density.density.toDouble()
    var widthPx by remember { mutableIntStateOf((402f * density.density).roundToInt()) }
    var heightPx by remember { mutableIntStateOf((874f * density.density).roundToInt()) }
    val width = widthPx / density.density.toDouble()
    val height = heightPx / density.density.toDouble()
    val layout = remember(width, height, topInset, bottomInset) {
        HoldDragResizeLayout(width, height, topInset, bottomInset)
    }
    val assets = remember { HoldDragResizeAssets(context.assets) }
    val backdrop = remember(layout, density.density) {
        HoldDragResizeBackdrop.create(layout, density.density.toDouble())
    }
    DisposableEffect(backdrop) {
        onDispose { backdrop.recycle() }
    }
    val renderer = remember(assets, backdrop) { HoldDragResizeRenderer(assets, backdrop) }
    val model = remember(layout, autoPlay, reducedMotion) {
        HoldDragResizeModel(layout, autoPlay = autoPlay && !reducedMotion, reducedMotion = reducedMotion)
    }
    var frameInvalidation by remember { mutableLongStateOf(0L) }
    var accessibilityVersion by remember { mutableIntStateOf(model.version) }

    fun flushHaptics() {
        model.events.forEach { event ->
            val feedback = when (event) {
                HoldDragResizeHaptic.Lift -> HapticFeedbackConstants.LONG_PRESS
                HoldDragResizeHaptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
                HoldDragResizeHaptic.Wall -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.CONTEXT_CLICK
                }
                HoldDragResizeHaptic.Drop -> HapticFeedbackConstants.KEYBOARD_TAP
            }
            view.performHapticFeedback(feedback)
        }
        model.events.clear()
    }

    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller == null) {
            onDispose { }
        } else {
            val previousLightStatus = controller.isAppearanceLightStatusBars
            val previousLightNavigation = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = previousLightStatus
                controller.isAppearanceLightNavigationBars = previousLightNavigation
            }
        }
    }

    LaunchedEffect(model) {
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            model.advance((now - previous) / 1e9)
            previous = now
            flushHaptics()
            if (model.active < 0 && accessibilityVersion != model.version) accessibilityVersion = model.version
            frameInvalidation = now
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val accessibilityState = accessibilityVersion
    val settled = model.settledLayout()
    val summary = buildString {
        append("$HoldDragResizeDayTitle, $HoldDragResizeDayDate. ")
        HoldDragResizePlan.forEachIndexed { index, item ->
            if (index > 0) append(". ")
            append("${item.title}, ${holdDragResizeClock(settled.first[index])} to ${holdDragResizeClock(settled.first[index] + settled.second[index])}")
        }
    }
    val actions = HoldDragResizePlan.flatMapIndexed { index, item ->
        if (item.kind == HoldDragResizeKind.Meeting) {
            emptyList()
        } else {
            listOf(
                CustomAccessibilityAction("Extend ${item.title} by 15 minutes") {
                    model.touchStart()
                    model.nudgeEnd(index, 1)
                    model.touchEnd()
                    flushHaptics()
                    true
                },
                CustomAccessibilityAction("Shorten ${item.title} by 15 minutes") {
                    model.touchStart()
                    model.nudgeEnd(index, -1)
                    model.touchEnd()
                    flushHaptics()
                    true
                },
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(HoldDragResizeTokens.Color.Page))
            .onSizeChanged {
                widthPx = it.width
                heightPx = it.height
            }
            .semantics {
                contentDescription = summary
                customActions = actions
            },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(model, density.density) {
                    val scale = density.density.toDouble()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x / scale
                        val downY = down.position.y / scale
                        model.touchStart()
                        model.updateFinger(downX, downY)
                        val card = model.cardAt(downX, downY)
                        model.pressBegin(card)
                        var latest = down
                        var held = false
                        try {
                            val releasedBeforeHold = withTimeoutOrNull(HoldDragResizeTokens.Resize.HoldMs) {
                                while (latest.pressed) {
                                    val event = awaitPointerEvent()
                                    latest = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                                    model.updateFinger(latest.position.x / scale, latest.position.y / scale)
                                    latest.consume()
                                }
                                true
                            }
                            if (releasedBeforeHold == null) {
                                held = true
                                model.beginResize(card, latest.position.y / scale)
                                while (latest.pressed) {
                                    val event = awaitPointerEvent()
                                    latest = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val x = latest.position.x / scale
                                    val y = latest.position.y / scale
                                    model.updateFinger(x, y)
                                    model.moveResize(y)
                                    latest.consume()
                                }
                            }
                        } finally {
                            if (held) model.endResize()
                            model.pressEnd()
                            model.touchEnd()
                            flushHaptics()
                        }
                    }
                },
        ) {
            @Suppress("UNUSED_VARIABLE")
            val drawSignal = frameInvalidation
            drawIntoCanvas { composeCanvas ->
                val native = composeCanvas.nativeCanvas
                val save = native.save()
                native.scale(density.density, density.density)
                renderer.draw(native, model)
                native.restoreToCount(save)
            }
        }
    }
}
