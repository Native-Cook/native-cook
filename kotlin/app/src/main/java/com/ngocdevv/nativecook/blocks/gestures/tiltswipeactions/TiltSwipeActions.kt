package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs

/** The reference loop is exactly 10 seconds and returns to the untouched ledger. */
const val LOOP = 10f

/**
 * Private Ledger: pull right through person detents to split a bill; swipe left for Flag and
 * Delete. The renderer uses the same dp geometry and bundled OFL font instances as the approved
 * React Native block. Static paper and card layers are rasterised once for each viewport size.
 */
@Composable
fun TiltSwipeActions(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val reducedMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val controller = remember(autoPlay, reducedMotion) { TiltSwipeActionsController(autoPlay, reducedMotion) }
    val painter = remember { TiltSwipeActionsPainter(context.assets) }

    LaunchedEffect(controller, autoPlay, reducedMotion) {
        if (!autoPlay || reducedMotion) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                controller.step((now - previous) / 1_000_000_000f)
                previous = now
            }
        }
    }
    // With autoplay off (including Reduce Motion), only a live Undo slip owns a frame clock.
    // The clock stops as soon as its four-second window expires.
    LaunchedEffect(controller.toast, autoPlay, reducedMotion) {
        if ((autoPlay && !reducedMotion) || controller.toast == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (controller.toast != null) {
            withFrameNanos { now ->
                controller.step((now - previous) / 1_000_000_000f)
                previous = now
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val scene = remember(maxWidth, maxHeight, bottom) {
            TiltSwipeActionsScene(maxWidth.value, maxHeight.value, 0f, bottom)
        }
        val scale = density.density

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Private account. Latest spending. Swipe a transaction right to split it, or left for Flag and Delete."
                }
                .pointerInput(controller, scene, scale) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position / scale
                        val row = controller.rowForSlot(scene.rowAt(start.y))
                        val tracker = VelocityTracker()
                        tracker.addPointerInputChange(down)
                        var lastTarget = if (row in controller.rows.indices) controller.rows[row].target else 1
                        var armed = false
                        var last = start
                        var cancelled = false
                        controller.touchStart(row)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            last = change.position / scale
                            val dx = last.x - start.x
                            val dy = last.y - start.y
                            if (change.pressed) {
                                if (!cancelled && abs(dy) > 10f && abs(dy) > abs(dx)) {
                                    cancelled = true
                                    controller.cancelTouch()
                                } else if (!cancelled && row in controller.rows.indices && abs(dx) >= 1f) {
                                    controller.drag(row, dx)
                                    val state = controller.rows[row]
                                    if (state.target != lastTarget) {
                                        lastTarget = state.target
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    if (state.armed && !armed) {
                                        armed = true
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    } else if (!state.armed) {
                                        armed = false
                                    }
                                    change.consume()
                                }
                                continue
                            }

                            if (cancelled) {
                                controller.cancelTouch()
                            } else if (row in controller.rows.indices) {
                                val velocity = tracker.calculateVelocity().x / scale
                                val target = controller.rows[row].target
                                val wasArmed = controller.rows[row].armed
                                controller.touchEnd(row, dx, velocity, last.x - 16f)
                                if ((dx > 0f && target >= 2) || wasArmed) {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                            } else {
                                handleTap(controller, scene, last, view)
                                controller.cancelTouch()
                            }
                            event.changes.forEach(PointerInputChange::consume)
                            break
                        }
                    }
                },
        ) {
            controller.tick
            drawIntoCanvas { target -> painter.draw(target.nativeCanvas, scene, scale, controller) }
        }
    }
}
private fun handleTap(
    controller: TiltSwipeActionsController,
    scene: TiltSwipeActionsScene,
    point: Offset,
    view: android.view.View,
) {
    if (controller.menuOpen) {
        controller.closeMenu()
        return
    }
    val toast = controller.scriptToast()
    if (toast != null && point.y in scene.toastTop..(scene.toastTop + 56f) && point.x >= scene.width - 120f) {
        controller.undo()
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        return
    }
    val fabCenter = Offset(scene.fabLeft + 24f, scene.fabTop + 25f)
    if ((point - fabCenter).getDistance() <= 32f) {
        controller.toggleMenu()
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
}
