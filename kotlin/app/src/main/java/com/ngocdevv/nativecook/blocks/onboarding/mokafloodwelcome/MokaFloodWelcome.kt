package com.ngocdevv.nativecook.blocks.onboarding.mokafloodwelcome

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion

/**
 * The first onboarding step of an invented morning-routine app, Brewday, as a flat illustration
 * on peach: a crimson moka pot tilts and pours into a faceted glass, the coffee rises with a
 * wobbling meniscus, domes over the rim, spills down the walls and floods the whole screen from
 * the bottom; the welcome is written on the coffee in cream handwriting, then the flood drains.
 *
 * Loop (LOOP = 9.5 s): rest → the pot tilts (1.0 s) and pours (1.2) → the glass fills (1.72–3.6)
 * → domes, spills and puddles (3.6–4.7) → the flood rises (4.7–6.5) as the pot rights itself and
 * the stream breaks (5.15–5.4) → the title is written (6.55–7.55) → the flood drains (8.1–9.0) →
 * rest. Hold to pour, drag left or down to pour harder; the loop resumes 2 s after the last touch.
 * Reduced motion: the coffee page with its copy, still.
 */
@Composable
fun MokaFloodWelcome(autoPlay: Boolean = false) {
    val art = MokaFloodWelcomeArt
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val reduced = rememberLiveReducedMotion()
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val res = remember { art.load(context) }
    val clock = remember { mutableDoubleStateOf(0.0) }
    val touch = remember { MokaFloodWelcomeTouch() }
    val awake = remember { mutableStateOf(false) }
    val running = !reduced && active && (autoPlay || awake.value)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var startNanos = -1L
        val base = clock.doubleValue
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0) startNanos = now
                val c = base + (now - startNanos) / 1e9
                clock.doubleValue = c
                if (touch.takeHaptic(c)) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            if (!autoPlay && !touch.touching && clock.doubleValue > touch.restsAfter + 0.1) {
                awake.value = false
                break
            }
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(art.argb(art.PEACH)))) {
        val k = maxWidth.value / 393f
        val height = (maxHeight.value / k).toDouble()
        val top = (WindowInsets.safeDrawing.getTop(density) / density.density / k).toDouble()
        val bottom = (WindowInsets.safeDrawing.getBottom(density) / density.density / k).toDouble()
        val layout = remember(height, top, bottom) { MokaFloodWelcomeArt.Layout(height, top, bottom) }

        val frameOf = { c: Double ->
            if (reduced) MokaFloodWelcomeMotion.Frame(volume = 3.0, title = 1.0, sub = 1.0) else touch.frame(c, autoPlay)
        }
        // The status bar's icons turn light while the coffee covers them (recomposes only on a flip).
        val covered by remember(layout, reduced) { derivedStateOf { art.coversTop(layout, frameOf(clock.doubleValue)) } }
        BlockSystemBars(darkIcons = !covered)

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Brewday. Brew your morning routine! Brewday lines up your first cup, " +
                        "your stretch and the plan for today. Moka pot: hold to pour coffee into the glass."
                    role = Role.Button
                }
                .pointerInput(reduced, layout) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val scale = size.width / 393f
                        val y0 = down.position.y / scale
                        if (y0 > layout.ctaTop - 12) {
                            // The Get started pill.
                            val x0 = down.position.x / scale
                            if (x0 in 24f..369f && y0 < layout.ctaTop + 56) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            return@awaitEachGesture
                        }
                        if (reduced) return@awaitEachGesture
                        touch.begin(clock.doubleValue, autoPlay)
                        awake.value = true
                        clock.doubleValue += 1e-6
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = (change.position.x - down.position.x) / scale
                            val dy = (change.position.y - down.position.y) / scale
                            touch.move(clock.doubleValue, dx.toDouble(), dy.toDouble())
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        touch.end(clock.doubleValue)
                    }
                },
        ) {
            val frame = frameOf(clock.doubleValue)
            val canvas = drawContext.canvas.nativeCanvas
            val scale = size.width / 393f
            canvas.save()
            canvas.scale(scale, scale)
            art.draw(canvas, layout, res, frame)
            canvas.restore()
        }
    }
}
