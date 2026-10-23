package com.ngocdevv.nativecook.blocks.pickers.sunbursttimerdial

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

// The square the dial is drawn in.
private const val SIZE = 320f
private const val RAYS = 28
private const val UNITS = 60f
private const val TAU = (Math.PI * 2).toFloat()

// Radii: a ray's two ends, the eight markers and the hub.
private const val INNER = 83f
private const val OUTER = 140f
private const val MARKER = 151f
private const val HUB = 24f

// How far a ray shoots out as it lights, and how far the hub's dot leans.
private const val FLICK = 4f
private const val LEAN = 3f

// The value's and the caption's distance from the centre.
private const val TEXT_Y = 54f

// Touches closer to the centre than this do not turn the dial.
private const val DEAD = 12f

// Seconds: a ray's change of weight, the sweep to a finger that just landed, and the chase after it.
private const val LIGHT_TIME = 0.08f
private const val REACH_TIME = 0.3f
private const val FOLLOW_TIME = 0.1f

private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** One sweep of the script: when it starts, where it ends, how long it takes and on which curve. */
private class Sweep(val at: Float, val to: Float, val time: Float, val easing: Easing)

private val Script = listOf(
    Sweep(0.8f, 15f, 1.0f, EaseOutStrong),
    Sweep(2.5f, 40f, 1.2f, EaseInOut),
    Sweep(4.4f, 25f, 0.7f, EaseInOut),
    Sweep(5.7f, 0f, 1.0f, EaseInOut),
)
private const val LOOP = 8f

// After a touch the loop waits this long, then takes `RETURN_TIME` to sweep back.
private const val RESUME_AFTER = 2f
private const val RETURN_TIME = 0.6f

/** A spring of mass 1 with the stiffness and damping the other versions use. */
private fun physical(stiffness: Float, damping: Float) =
    spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.001f)

private val SnapSpring = physical(400f, 26f)
private val PressSpring = physical(320f, 22f)

private val Ink = Color(0xFF111111)
private val Hairline = Color(0xFFA3A3A0)
private val HubColor = Color(0xFF333333)

private val DigitStyle = TextStyle(
    color = Ink,
    fontSize = 22.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private val CaptionStyle = TextStyle(color = Ink, fontSize = 14.sp, platformStyle = PlatformTextStyle(includeFontPadding = false))

/** How many rays are lit at [value], taken to the unit the digits show: the one at 12 o'clock, and one more every 60/28 units. */
private fun litAt(value: Float) = (floor(value.roundToInt() * RAYS / UNITS + 0.0001f).toInt() + 1).coerceIn(1, RAYS)

/** The angle of a point seen from the centre: 0 at 12 o'clock, growing clockwise to a full turn. */
private fun turnOf(x: Float, y: Float): Float {
    val turn = atan2(x, -y)
    return if (turn < 0f) turn + TAU else turn
}

/**
 * A timer dial drawn as a burst of 28 rays around a dark hub. The face counts 60 units: rays light
 * clockwise from 12 o'clock as the value grows, the two-digit value sits above the hub and a caption
 * below it. Dragging round the dial sets the value to the angle under the finger, without wrapping
 * past the top; each ray flicks outward as it lights, and the hub's dot leans towards the newest ray.
 *
 * @param initial The value the dial starts on, 0…59.
 * @param label The caption under the hub.
 * @param autoPlay Sweeps the dial through a short script by itself, in a loop.
 * @param onChange Called with the value the dial settles on.
 */
@Composable
fun SunburstTimerDial(
    initial: Int = 0,
    label: String = "Timer",
    autoPlay: Boolean = false,
    onChange: ((value: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still
    val start = initial.coerceIn(0, UNITS.toInt() - 1).toFloat()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChange)

    // The value, 0…59, continuous while it sweeps.
    val value = remember(start) { Animatable(start) }
    // How far each ray has turned from hairline to bold, 0…1, and its flick outward.
    val lit = remember(start) { List(RAYS) { Animatable(if (it < litAt(start)) 1f else 0f) } }
    val flick = remember { List(RAYS) { Animatable(0f) } }
    // 1 while a finger (or the script) is turning the dial: the hub dips and its dot leans.
    val press = remember { Animatable(0f) }
    // `held`: a finger is on the dial. `byHand`: it turns under a finger, not under the script; only then it ticks.
    val hand = remember { object { var held = false; var byHand = false } }
    // Counts touches: each one stops the script and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    val measurer = rememberTextMeasurer()
    val caption = remember(label, measurer) { measurer.measure(label, CaptionStyle, maxLines = 1) }

    // Under reduced motion nothing eases: every change is a jump.
    fun move(target: Float, spec: AnimationSpec<Float>) {
        scope.launch { if (still) value.snapTo(target) else value.animateTo(target, spec) }
    }

    fun hold(down: Boolean) {
        scope.launch { if (still) press.snapTo(if (down) 1f else 0f) else press.animateTo(if (down) 1f else 0f, PressSpring) }
    }

    // Sweeps to `target` in `time` seconds.
    fun sweep(to: Float, time: Float, easing: Easing) {
        val target = to.coerceIn(0f, UNITS - 1f)
        move(target, tween((time * 1000).toInt(), easing = easing))
        changed?.invoke(target.roundToInt())
    }

    // The rays the value has passed switch on or off, one tick each.
    LaunchedEffect(value, lit, still) {
        var count = litAt(value.value)
        snapshotFlow { litAt(value.value) }.drop(1).collect { now ->
            val change = tween<Float>((LIGHT_TIME * 1000).toInt(), easing = LinearEasing)
            if (now > count) {
                for (index in count until now) {
                    launch { if (still) lit[index].snapTo(1f) else lit[index].animateTo(1f, change) }
                    if (!still) {
                        launch {
                            flick[index].animateTo(1f, tween(30, easing = LinearEasing))
                            flick[index].animateTo(0f, tween(120, easing = LinearEasing))
                        }
                    }
                }
            } else {
                for (index in now until count) launch { if (still) lit[index].snapTo(0f) else lit[index].animateTo(0f, change) }
            }
            count = now
            if (hand.byHand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    // The script a finger would follow: four sweeps, the last one back to where it began.
    LaunchedEffect(auto, touches, value) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, sweep back to the first value, and start over from rest.
            delay((RESUME_AFTER * 1000).toLong())
            if (hand.held) return@LaunchedEffect
            hand.byHand = false
            sweep(start, RETURN_TIME, EaseInOut)
            delay((RETURN_TIME * 1000).toLong())
        }
        hand.byHand = false
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        suspend fun until(seconds: Float) {
            val due = begin + ((turn * LOOP + seconds) * 1e9).toLong()
            delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
        }
        while (true) {
            for (step in Script) {
                until(step.at)
                hold(true)
                sweep(start + step.to, step.time, step.easing)
                until(step.at + step.time)
                hold(false)
            }
            turn += 1
        }
    }

    Canvas(
        modifier
            .size(SIZE.dp)
            .semantics {
                contentDescription = label
                stateDescription = value.targetValue.roundToInt().toString()
            }
            // Dragging anywhere on the dial: the value goes to the angle under the finger, then follows
            // it round, stopping at 0 and 59 instead of wrapping past the top.
            .pointerInput(value, still) {
                val dead = DEAD.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val first = down.position - Offset(size.width / 2f, size.height / 2f)
                    var angle = turnOf(first.x, first.y)
                    // Right over the centre the angle means nothing.
                    var dragged = if (first.getDistance() > dead) (angle / TAU * UNITS).coerceAtMost(UNITS - 1f) else value.value
                    hand.held = true
                    hand.byHand = true
                    touches += 1
                    hold(true)
                    move(dragged, tween((REACH_TIME * 1000).toInt(), easing = EaseOutStrong))
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val at = change.position - Offset(size.width / 2f, size.height / 2f)
                            if (hypot(at.x, at.y) <= dead) continue
                            val turn = turnOf(at.x, at.y)
                            var step = turn - angle
                            if (step > Math.PI) step -= TAU else if (step < -Math.PI) step += TAU
                            angle = turn
                            dragged = (dragged + step / TAU * UNITS).coerceIn(0f, UNITS - 1f)
                            move(dragged, tween((FOLLOW_TIME * 1000).toInt(), easing = EaseOutStrong))
                        }
                    } finally {
                        val target = dragged.roundToInt().toFloat()
                        hand.held = false
                        hold(false)
                        move(target, SnapSpring)
                        changed?.invoke(target.roundToInt())
                        touches += 1
                    }
                }
            },
    ) {
        val d = density
        val now = value.value
        val pressed = press.value

        for (index in 0 until RAYS) {
            val glow = lit[index].value
            val turn = TAU * index / RAYS
            val outer = OUTER + FLICK * flick[index].value
            drawLine(
                lerp(Hairline, Ink, glow),
                center + Offset(sin(turn) * INNER, -cos(turn) * INNER) * d,
                center + Offset(sin(turn) * outer, -cos(turn) * outer) * d,
                strokeWidth = (1f + glow) * d,
            )
        }

        for (index in 0 until 8) {
            val turn = TAU * index / 8
            drawCircle(Ink, radius = 1.5f * d, center = center + Offset(sin(turn) * MARKER, -cos(turn) * MARKER) * d)
        }

        // The hub dips under the finger, and its dot leans towards the newest ray.
        drawCircle(HubColor, radius = HUB * (1f - 0.05f * pressed) * d, center = center)
        val turn = TAU * now / UNITS
        drawCircle(Color.White, radius = 1.5f * d, center = center + Offset(sin(turn), -cos(turn)) * (LEAN * pressed * d))

        val shown = now.roundToInt().coerceIn(0, UNITS.toInt() - 1)
        val digits = measurer.measure(if (shown < 10) "0$shown" else "$shown", DigitStyle, maxLines = 1)
        drawText(digits, topLeft = center + Offset(-digits.size.width / 2f, -TEXT_Y * d - digits.size.height / 2f))
        drawText(caption, topLeft = center + Offset(-caption.size.width / 2f, (TEXT_Y - 4f) * d - caption.size.height / 2f))
    }
}
