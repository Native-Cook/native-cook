package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.roundToInt

/**
 * Strolls' first screen. Pull the glass dome into a magnifier, then release it to open the map beneath it.
 * Both screens are pre-rendered once; each frame changes only lens uniforms, route trim, hint, and demo finger.
 */
@Composable
fun GlassDiscOnboarding(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(GlassDiscOnboardingTheme.Color.Page)),
    ) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        if (width <= 0 || height <= 0) return@BoxWithConstraints
        val top = WindowInsets.statusBars.getTop(density)
        val bottom = WindowInsets.navigationBars.getBottom(density)
        val layout = remember(width, height, density.density, top, bottom) {
            GlassDiscOnboardingLayout.make(width, height, density.density, top, bottom)
        }
        val pages = remember(context, layout) { GlassDiscOnboardingPages.create(context, layout) }
        val controller = remember(layout, autoPlay, reducedMotion) {
            GlassDiscOnboardingController(
                layout = layout,
                autoPlay = autoPlay && !reducedMotion,
                reducedMotion = reducedMotion,
                onHaptic = { haptic ->
                    val constant = when (haptic) {
                        GlassDiscOnboardingController.Haptic.Grab -> HapticFeedbackConstants.CLOCK_TICK
                        GlassDiscOnboardingController.Haptic.Commit,
                        GlassDiscOnboardingController.Haptic.Back,
                        -> HapticFeedbackConstants.CONTEXT_CLICK
                    }
                    view.performHapticFeedback(constant)
                },
            )
        }
        val renderer = remember(context, layout, pages) { GlassDiscOnboardingRenderer(context, layout, pages) }
        val tick = remember(controller) { mutableLongStateOf(0L) }
        var wake by remember(controller) { mutableLongStateOf(0L) }
        var mapOpen by remember(controller) { mutableStateOf(controller.isOpen) }

        LaunchedEffect(controller, wake) {
            var previous = 0L
            while (controller.needsFrames) {
                withFrameNanos { now ->
                    val delta = if (previous == 0L) 0f else ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    previous = now
                    controller.update(delta)
                    if (mapOpen != controller.isOpen) mapOpen = controller.isOpen
                    tick.longValue++
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (mapOpen) {
                            "Canal loop map. 3.2 kilometres, 45 minutes, easy."
                        } else {
                            "Strolls. Walks worth taking, five minutes from your door. Swipe up to explore."
                        }
                        stateDescription = if (mapOpen) "Map open" else "Introduction"
                        role = Role.Button
                        onClick(label = if (mapOpen) "Back" else "Explore") {
                            if (mapOpen) controller.accessibilityBack() else controller.accessibilityEnter()
                            wake++
                            tick.longValue++
                            true
                        }
                    }
                    .pointerInput(controller) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPointerInputChange(down)
                            controller.onPointerDown(down.position.x, down.position.y)
                            tick.longValue++
                            var released = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPointerInputChange(change)
                                    if (!change.pressed) {
                                        controller.onPointerUp(tracker.calculateVelocity().y, cancelled = false)
                                        wake++
                                        tick.longValue++
                                        released = true
                                        change.consume()
                                        break
                                    }
                                    if (change.position != change.previousPosition) {
                                        controller.onPointerMove(change.position.x, change.position.y)
                                        tick.longValue++
                                    }
                                    change.consume()
                                }
                            } finally {
                                if (!released) {
                                    controller.onPointerUp(0f, cancelled = true)
                                    wake++
                                    tick.longValue++
                                }
                            }
                        }
                    },
            ) {
                @Suppress("UNUSED_VARIABLE")
                val frameNumber = tick.longValue
                renderer.draw(drawContext.canvas.nativeCanvas, controller.snapshot(), autoPlay && !reducedMotion)
            }

            if (mapOpen) {
                val edge = layout.dp(GlassDiscOnboardingTheme.Layout.Edge)
                val topPx = layout.top + layout.dp(GlassDiscOnboardingTheme.Layout.SearchY)
                val sizePx = layout.dp(GlassDiscOnboardingTheme.Layout.SearchHeight)
                Box(
                    Modifier
                        .offset { IntOffset(edge.roundToInt(), topPx.roundToInt()) }
                        .size((sizePx / density.density).dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Back"
                            onClick {
                                controller.accessibilityBack()
                                wake++
                                tick.longValue++
                                true
                            }
                        },
                )
            }
        }
    }
}
