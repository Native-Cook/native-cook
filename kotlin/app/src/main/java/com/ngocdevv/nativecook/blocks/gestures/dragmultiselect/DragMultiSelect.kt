package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.min

/** Offline listening planner with long-press range selection and drag-to-reverse. */
@Composable
fun DragMultiSelect(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val assets = remember(context) { DragMultiSelectAssets(context.assets) }
    val renderer = remember(assets) { DragMultiSelectRenderer(assets) }
    val engine = remember(autoPlay, reducedMotion, view) {
        DragMultiSelectEngine(autoPlay, reducedMotion) { kind ->
            val feedback = when (kind) {
                DragMultiSelectHaptic.Enter -> HapticFeedbackConstants.LONG_PRESS
                DragMultiSelectHaptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
                DragMultiSelectHaptic.Cover -> HapticFeedbackConstants.CONTEXT_CLICK
                DragMultiSelectHaptic.Commit -> HapticFeedbackConstants.VIRTUAL_KEY
                DragMultiSelectHaptic.Success -> HapticFeedbackConstants.CONTEXT_CLICK
            }
            view.performHapticFeedback(feedback)
        }
    }
    val frame = remember { mutableLongStateOf(0L) }
    val frameRunning = remember(engine) { mutableStateOf(autoPlay && !reducedMotion) }
    val safeInsets = WindowInsets.safeDrawing
    val safeTop = safeInsets.getTop(density) / density.density
    val safeBottom = safeInsets.getBottom(density) / density.density

    DragMultiSelectSystemBars(view)

    LaunchedEffect(engine, frameRunning.value) {
        if (!frameRunning.value) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (frameRunning.value) {
            val now = withFrameNanos { it }
            engine.step(min((now - previous) / 1_000_000_000f, .05f), now / 1_000_000_000.0)
            previous = now
            frame.longValue++
            if (!engine.needsFrame) frameRunning.value = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(DragMultiSelectTokens.Colors.page))
            .semantics {
                contentDescription = "Offline flight listening. Hold an episode, then drag to select a range."
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        SideEffect {
            engine.setSize(width, height, safeTop, safeBottom)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(engine, density) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        frameRunning.value = true
                        engine.touchDown(
                            down.position.x / density.density,
                            down.position.y / density.density,
                            down.uptimeMillis / 1_000.0,
                        )
                        var released = false
                        try {
                            while (!released) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                val velocityY = tracker.calculateVelocity().y / density.density
                                if (!change.pressed) {
                                    engine.touchUp(velocityY)
                                    frameRunning.value = true
                                    change.consume()
                                    released = true
                                } else {
                                    engine.touchMove(
                                        change.position.x / density.density,
                                        change.position.y / density.density,
                                        velocityY,
                                    )
                                    frameRunning.value = true
                                    change.consume()
                                }
                            }
                        } finally {
                            if (!released) {
                                engine.touchUp(0f)
                                frameRunning.value = true
                            }
                        }
                    }
                },
        ) {
            frame.longValue
            val native = drawContext.canvas.nativeCanvas
            val save = native.save()
            native.scale(density.density, density.density)
            renderer.draw(native, engine)
            native.restoreToCount(save)
        }
    }
}

@Composable
private fun DragMultiSelectSystemBars(view: android.view.View) {
    DisposableEffect(view) {
        val window = findActivity(view.context)?.window
        if (window == null) {
            onDispose {}
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousStatus = controller.isAppearanceLightStatusBars
            val previousNavigation = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNavigation
            }
        }
    }
}

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
