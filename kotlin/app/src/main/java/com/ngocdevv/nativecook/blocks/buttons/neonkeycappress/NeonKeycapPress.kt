package com.ngocdevv.nativecook.blocks.buttons.neonkeycappress

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow

/** Seconds, identical to the React Native reference. */
internal const val LOOP = 7.2
private const val PRESS = 2.4
private const val KEY = 92f
private const val GAP = 16f
private const val SPAN = KEY * 3 + GAP * 2
private val PRESSES = intArrayOf(4, 1, 6)
private val ACTIONS = listOf(
    "Send the draft", "Ask the assistant", "Boost mode on",
    "Start dictation", "Play focus mix", "Save to favourites",
    "Night mode on", "Star this thread", "New note",
)

// Glowpad: a 308 dp grid centered at (w/2, h*.48), nine 92 dp keys with 16 dp gaps.
// Three 2.4 s presses: Play -> AI -> Moon. In each, the ghost glides 0–.35 (cubic
// in/out), the key sinks 1 -> .92 during .35–.5 and returns during .5–.75.
// Rings start at .4, .58, .76; glow rises .45–.75, holds through 1.6, fades by 2.3.
// A tap starts the same curve at .35, with one light haptic. The RN code resumes
// autoplay after HOLD + .4 = 2.4 s. Reduced motion freezes drawing and disables taps.
@Composable
fun NeonKeycapPress(
    modifier: Modifier = Modifier,
    title: String = "Your glow pad",
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
    BlockSystemBars(darkIcons = false)

    val motion = remember { NeonKeycapPressMotion() }
    val tick = remember { mutableIntStateOf(0) }
    var manual by remember { mutableStateOf(false) }
    val art = remember(density.density) { NeonKeycapPressArt(density.density) }
    val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(density) / density.density
    val textBase = TextStyle(
        fontFamily = FontFamily.SansSerif,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = Color.White,
    )

    LaunchedEffect(active, reduced, autoPlay, manual) {
        if (!active || reduced || (!autoPlay && !manual)) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
                previous = now
                motion.advance(dt, autoPlay)
                if (manual && !motion.manual) manual = false
                tick.intValue++
            }
        }
    }

    fun tap(key: Int) {
        if (reduced) return
        motion.tap(key)
        manual = true
        tick.intValue++
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF07020F))) {
        val width = maxWidth.value
        val height = maxHeight.value
        val cx = width / 2
        val cy = height * 0.48f
        Canvas(
            Modifier.fillMaxSize().pointerInput(width, height, reduced) {
                if (reduced) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.position - down.position
                        if (change.isConsumed || change.uptimeMillis - down.uptimeMillis > 500 ||
                            hypot(delta.x, delta.y) > 10f * this.density ||
                            change.position.x !in 0f..size.width.toFloat() ||
                            change.position.y !in 0f..size.height.toFloat()
                        ) cancelled = true
                        if (!change.pressed) {
                            if (!cancelled) {
                                // Match RN's floor-based hit testing, including the key gaps.
                                val col = floor((change.position.x / this.density - (cx - SPAN / 2)) / (KEY + GAP)).toInt()
                                val row = floor((change.position.y / this.density - (cy - SPAN / 2)) / (KEY + GAP)).toInt()
                                if (col in 0..2 && row in 0..2) {
                                    change.consume()
                                    tap(row * 3 + col)
                                }
                            }
                            break
                        }
                    }
                }
            },
        ) {
            // Per-frame state is read only by drawing and the action's graphics layers.
            tick.intValue
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(density.density, density.density)
            art.draw(canvas, width, height, motion.frame(width, height))
            canvas.restore()
        }

        Column(Modifier.offset(y = (safeTop + 8).dp).padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Glowpad", style = textBase.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold))
                Spacer(Modifier.weight(1f))
                Text("Connected", style = textBase.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC59BFF)))
            }
            Text(
                title,
                modifier = Modifier.padding(top = 16.dp),
                style = textBase.copy(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = (safeBottom + 18).dp),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Color(0x1AB266FF), RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0x4DC59BFF), RoundedCornerShape(22.dp))
                    .padding(19.dp),
            ) {
                Text(
                    "LAST ACTION",
                    style = textBase.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.55f)),
                )
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(30.dp)) {
                    ACTIONS.forEachIndexed { key, action ->
                        Text(
                            action,
                            modifier = Modifier.graphicsLayer {
                                tick.intValue
                                val frame = motion.frame(width, height)
                                val on = if (frame.key == key) 1f else 0f
                                alpha = on * (0.55f + 0.45f * (frame.glow * 1.5f).coerceIn(0f, 1f))
                                translationY = 6f * (1 - (frame.glow * 3).coerceIn(0f, 1f)) * on * density.density
                            },
                            style = textBase.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                "Tap a key to run its macro",
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = textBase.copy(fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f)),
            )
        }

        ACTIONS.forEachIndexed { key, action ->
            Box(
                Modifier.offset(
                    (cx - SPAN / 2 + (key % 3) * (KEY + GAP)).dp,
                    (cy - SPAN / 2 + (key / 3) * (KEY + GAP)).dp,
                ).size(KEY.dp).semantics {
                    contentDescription = action
                    role = Role.Button
                    if (reduced) disabled()
                    onClick {
                        if (reduced) false else { tap(key); true }
                    }
                },
            )
        }
    }
}

internal data class NeonKeycapPressFrame(
    val key: Int = 4,
    val sink: Float = 1f,
    val glow: Float = 0f,
    val ripple: Float = -1f,
    val fingerX: Float = 0f,
    val fingerY: Float = 0f,
    val ghost: Boolean = false,
)

private fun segment(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
private fun cubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

/** One clock; the displayed state is a pure function of its current press time. */
private class NeonKeycapPressMotion {
    private var elapsed = 0.0
    private var origin = 0.0
    private var tappedAt = -10.0
    private var tappedKey = 0
    private var lastKey = 4
    private var auto = false
    private var shownManual = false
    var manual = false
        private set

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        auto = autoPlay
        // RN publishes the final manual frame before restarting autoplay on the next frame.
        shownManual = manual
        if (manual && elapsed - tappedAt > 2.4) {
            manual = false
            origin = elapsed
        }
    }

    fun tap(key: Int) {
        tappedKey = key
        lastKey = key
        tappedAt = elapsed
        manual = true
        shownManual = true
    }

    fun frame(width: Float, height: Float): NeonKeycapPressFrame {
        val manual = shownManual
        if (!manual && !auto) return NeonKeycapPressFrame(key = lastKey)
        val t = ((elapsed - origin) % LOOP + LOOP) % LOOP
        val n = floor(t / PRESS).toInt().coerceIn(0, 2)
        val x = if (manual) elapsed - tappedAt + 0.35 else t - n * PRESS
        val key = if (manual) tappedKey else PRESSES[n]
        val sink = when {
            x < 0.0 || x > 2.4 || x < 0.35 -> 1.0
            x < 0.5 -> 1 - 0.08 * segment(x, 0.35, 0.5)
            else -> 0.92 + 0.08 * segment(x, 0.5, 0.75)
        }
        val glow = if (x < 0 || x > 2.4) 0.0 else segment(x, 0.45, 0.75) * (1 - cubic(segment(x, 1.6, 2.3)))
        val ripple = if (x < 0 || x > 2.4) -1.0 else x - 0.4
        val previous = PRESSES[(n + PRESSES.size - 1) % PRESSES.size]
        val mix = cubic(segment(x, 0.0, 0.35)).toFloat()
        val x0 = width / 2 - SPAN / 2 + KEY / 2
        val y0 = height * 0.48f - SPAN / 2 + KEY / 2
        val fx = x0 + (previous % 3) * (KEY + GAP)
        val fy = y0 + (previous / 3) * (KEY + GAP)
        val tx = x0 + (key % 3) * (KEY + GAP)
        val ty = y0 + (key / 3) * (KEY + GAP)
        return NeonKeycapPressFrame(
            key, sink.toFloat(), glow.toFloat(), ripple.toFloat(),
            fx + (tx - fx) * mix + 14, fy + (ty - fy) * mix + 22,
            ghost = !manual && auto,
        )
    }
}
