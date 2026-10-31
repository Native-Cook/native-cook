package com.ngocdevv.nativecook.blocks.buttons.glasssplitbutton

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/** Seconds. Matches GlassSplitButton.tsx. */
internal const val LOOP = 7.0

// Drift's capture control: disc 132 -> pill 104 x 52 -> bar 272 x 92, centered at h / 2.
// 0–1 rest; 1–1.4 gather (cubic in/out); 1.4–2.1 open (back, k=1.5).
// 1.7–2.3 rings draw and labels rise; 3.3–3.7 glass-only press; 4.8–5.15 erase.
// 5–5.45 fold (cubic in/out); 5.5–6.2 return (back); 6.2–7 rest.
// Touch follows the identical timeline, including RN's two-second hold after the transition.
// Reduced motion freezes the fully open state at t=3, with background clock=0.
@Composable
fun GlassSplitButton(
    modifier: Modifier = Modifier,
    brand: String = "Drift",
    title: String = "Capture\nanything",
    autoPlay: Boolean = false,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BlockSystemBars(darkIcons = true)
    val motion = remember { GlassSplitButtonMotion() }
    val tick = remember { mutableIntStateOf(0) }
    val art = remember { GlassSplitButtonArt() }
    val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(density) / density.density

    LaunchedEffect(active, reduced, autoPlay) {
        if (!active || reduced) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
                previous = now
                motion.advance(dt, autoPlay)
                tick.intValue++
            }
        }
    }
    fun tap() {
        if (reduced) return
        motion.tap()
        tick.intValue++
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val text = remember(width, height, safeTop, safeBottom, density, brand, title) {
            GlassSplitButtonText(width, height, safeTop, safeBottom, density.density, brand, title)
        }
        Canvas(Modifier.fillMaxSize()) {
            // The clock invalidates drawing only; no screen layout is recomposed per frame.
            tick.intValue
            val frame = glassSplitButtonAt(if (reduced) 3.0 else motion.shown)
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(density.density, density.density)
            art.draw(canvas, width, height, if (reduced) 0.0 else motion.clock, frame)
            text.draw(canvas, frame.icons)
            canvas.restore()
        }
        Box(
            Modifier.offset((width / 2 - 136).dp, (height / 2 - 66).dp)
                .size(272.dp, 132.dp)
                .semantics {
                    contentDescription = "New capture"
                    role = Role.Button
                    if (reduced) disabled()
                    onClick {
                        if (reduced) false else { tap(); true }
                    }
                }
                .pointerInput(reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val delta = change.position - down.position
                            if (change.isConsumed || change.uptimeMillis - down.uptimeMillis > 500 ||
                                hypot(delta.x, delta.y) > 10 * this.density ||
                                change.position.x !in 0f..size.width.toFloat() ||
                                change.position.y !in 0f..size.height.toFloat()
                            ) cancelled = true
                            if (!change.pressed) {
                                if (!cancelled) { change.consume(); tap() }
                                break
                            }
                        }
                    }
                },
        )
    }
}

internal data class GlassSplitButtonFrame(
    val width: Float,
    val height: Float,
    val icons: Float,
    val press: Float,
)

private fun glassSplitButtonSegment(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
private fun glassSplitButtonCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun glassSplitButtonBack(x: Double) = 1 + 2.5 * (x - 1).pow(3) + 1.5 * (x - 1).pow(2)

internal fun glassSplitButtonAt(t: Double): GlassSplitButtonFrame {
    val w: Double
    val h: Double
    when {
        t < 1.4 -> {
            val a = glassSplitButtonCubic(glassSplitButtonSegment(t, 1.0, 1.4))
            w = 132 - 28 * a
            h = 132 - 80 * a
        }
        t < 5 -> {
            val b = glassSplitButtonBack(glassSplitButtonSegment(t, 1.4, 2.1))
            w = 104 + 168 * b
            h = 52 + 40 * b
        }
        t < 5.5 -> {
            val c = glassSplitButtonCubic(glassSplitButtonSegment(t, 5.0, 5.45))
            w = 272 - 168 * c
            h = 92 - 40 * c
        }
        else -> {
            val d = glassSplitButtonBack(glassSplitButtonSegment(t, 5.5, 6.2))
            w = 104 + 28 * d
            h = 52 + 80 * d
        }
    }
    val icons = (1 - (1 - glassSplitButtonSegment(t, 1.7, 2.3)).pow(3)) *
        (1 - glassSplitButtonSegment(t, 4.8, 5.15).pow(3))
    val press = 1 - 0.05 * sin(PI * glassSplitButtonSegment(t, 3.3, 3.7))
    return GlassSplitButtonFrame(w.toFloat(), h.toFloat(), icons.toFloat(), press.toFloat())
}

/** Plain mutable fields are sampled by the Canvas; tick is the only frame invalidation. */
private class GlassSplitButtonMotion {
    var elapsed = 0.0
    var origin = 0.0
    var clock = 0.0
    var shown = 0.0
    var manual = false
    var tapped = -10.0
    var opening = true

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        clock = (elapsed - origin) % LOOP
        if (manual) {
            val since = elapsed - tapped
            if (opening) {
                shown = minOf(3.0, 1.0 + since)
                if (since > 4.0) {
                    manual = false
                    origin = elapsed - 3.0
                }
            } else {
                shown = minOf(6.2, 4.8 + since)
                if (since > 3.4) {
                    manual = false
                    origin = elapsed - 6.2
                }
            }
            return
        }
        shown = if (autoPlay) clock else 0.0
    }

    fun tap() {
        opening = glassSplitButtonAt(shown).width <= 152
        manual = true
        tapped = elapsed
    }
}
