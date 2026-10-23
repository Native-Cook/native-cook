package com.ngocdevv.nativecook.blocks.pickers.bendinglinehumidityslider

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The fixed words beside the slider. */
data class HumidityLabels(
    val temperatureTitle: String = "RETURN TEMPERATURE",
    val temperature: String = "20°C",
    val humidityTitle: String = "CURRENT HUMIDITY",
    val absoluteTitle: String = "ABSOLUTE HUMIDITY",
    val absolute: String = "4gr/ft3",
    val warning: String = "— extreme humidity levels. Use precaution for set-points outside of 20%-55%.",
)

private const val WIDTH = 340f
private const val HEIGHT = 600f

// The line, its bump and the knob.
private const val AXIS = 122f

// How far the line swerves, and how far above and below the knob it leaves its axis.
private const val BUMP = 23f
private const val SPAN = 46f

// The furthest the bump's ends trail behind the knob.
private const val MAX_LEAN = 18f

// The knob sits this far right of the axis.
private const val KNOB_OUT = 3f
private const val END_FADE = 28f

// Percent over which the line turns from cyan to magenta above and below the recommended band.
private const val BLEND_ABOVE = 12f
private const val BLEND_BELOW = 8f

// The scale.
private const val SCALE_TOP = 48f
private const val LABEL_X = 26f
private const val DOT_X = 17f
private const val TICK_INSET = 5f

// A label hides while the knob is within this many dp of it, and fades in over the next few.
private const val HIDE_WITHIN = 18f
private const val HIDE_FADE = 12f

// The read-outs.
private const val READ_X = 182f
private const val DIGIT_W = 34f
private const val DIGIT_H = 60f

// Seconds the large figure takes to cover most of the way to the set-point.
private const val ROLL_TIME = 0.18f

// The knob's settling, the spring the bump's ends follow it on, and the press.
private const val STIFFNESS = 300f
private const val DAMPING = 26f
private const val LAG_STIFFNESS = 260f
private const val LAG_DAMPING = 24f
private const val PRESS_SCALE = 0.92f
private const val PRESS_TIME = 0.12f
private const val RELEASE_TIME = 0.2f
private const val TAP_SLOP = 4f
private const val WARN_TIME = 0.5f

// autoPlay (seconds): when each move starts, the set-point it goes to (null: the resting one) and how long it takes.
private const val LOOP = 8f

private class Step(val at: Float, val to: Float?, val time: Float)

private val Script = listOf(
    Step(1.0f, 48f, 0.9f),
    Step(2.6f, 28f, 1.1f),
    Step(4.4f, 18f, 0.5f),
    Step(5.5f, null, 0.9f),
)

// After a touch the loop waits this long, then takes `HOME_TIME` to return to the resting set-point.
private const val RESUME_AFTER = 2f
private const val HOME_TIME = 0.7f

private val EaseInOut = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

// Ease-in-out that runs a little past the end and comes back.
private val SettleCurve = CubicBezierEasing(0.45f, 0f, 0.3f, 1.18f)

private val Night = Color(0xFF070A26)
private val Cyan = Color(0xFF1AA9E0)
private val Magenta = Color(0xFFD4127A)
private val Tick = Color(0xFF2A2F55)
private val TickLit = Color(0xFF5D68B8)
private val NoteInk = Color(0xFF6A72A8)
private val Yellow = Color(0xFFF5D21F)
private val Dim = Color(0xFF4B5390)
private val Ring = Color(0xFFB9BDCC)

private fun type(size: Float, color: Color, weight: FontWeight, spacing: Float = 0f, line: Float = Float.NaN) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeight = if (line.isNaN()) androidx.compose.ui.unit.TextUnit.Unspecified else line.sp,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val StopStyle = type(13f, Color.White, FontWeight.Bold)
private val LiveStyle = type(18f, Color.White, FontWeight.Bold)
private val CaptionStyle = type(10f, Dim, FontWeight.Medium, 0.4f)
private val ValueStyle = type(20f, Color.White, FontWeight.Bold)
private val DigitStyle = type(52f, Color.White, FontWeight.ExtraBold)
private val NoteStyle = type(10f, NoteInk, FontWeight.Normal, line = 15f)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

/** The scale: the labels are [gap] apart whatever their values, and a height between two of them stands for a value between theirs. */
private class Scale(val stops: List<Float>) {
    val gap = (HEIGHT - SCALE_TOP * 2f) / max(1, stops.size - 1)

    fun clamped(y: Float) = y.coerceIn(SCALE_TOP, SCALE_TOP + gap * (stops.size - 1))

    fun valueAt(y: Float): Float {
        if (stops.size < 2) return stops.firstOrNull() ?: 0f
        val u = (clamped(y) - SCALE_TOP) / gap
        val k = min(floor(u).toInt(), stops.size - 2)
        return stops[k] + (stops[k + 1] - stops[k]) * (u - k)
    }

    fun yOf(value: Float): Float {
        for (k in 0 until stops.size - 1) {
            if (value <= stops[k] && value >= stops[k + 1] && stops[k] != stops[k + 1]) {
                return SCALE_TOP + (k + (stops[k] - value) / (stops[k] - stops[k + 1])) * gap
            }
        }
        return if (stops.isEmpty() || value > stops[0]) SCALE_TOP else SCALE_TOP + gap * (stops.size - 1)
    }
}

/** How far the line is pushed aside at the height [at], 0…1. The bump's ends are centred [lean] away from the knob. */
private fun bend(at: Float, knob: Float, lean: Float): Float {
    val s = if (at < knob) (knob - at) / (SPAN - lean) else (at - knob) / (SPAN + lean)
    return if (s >= 1f) 0f else (1f + cos(Math.PI.toFloat() * s)) / 2f
}

/** What moves. */
private class SliderMotion(y: Float, value: Int) {
    /** The knob's height on the panel. */
    val knob = Animatable(y)

    /** The point the ends of the bump are centred on. It follows the knob on a spring, so the bump leans on a quick move. */
    var lag by mutableFloatStateOf(y)
    var lagSpeed = 0f

    /** The large figure, which rolls towards the set-point. */
    var shown by mutableFloatStateOf(value.toFloat())

    /** 0 at rest … 1 while the knob is held; its size dips while it is held and bounces on release. */
    val press = Animatable(0f)
    val size = Animatable(1f)

    /** 0…1 over `WARN_TIME` from the moment the set-point leaves the recommended band. */
    val warn = Animatable(1f)

    /** The set-point in whole percent, for the live label. */
    var whole by mutableIntStateOf(value)
    var held by mutableStateOf(false)

    /** The moves come from a finger, so they tick; the script's do not. */
    var felt = false
}

/**
 * A vertical humidity slider drawn as one thin line that swerves around its knob like a string pushed
 * aside by a bead. The line is cyan through the recommended band and turns magenta outside it; a
 * ladder of ticks left of it bends with it, and the labels of an uneven scale stand further left, the
 * one beside the knob replaced by the live set-point. Right of the line three read-outs are stacked;
 * the large one rolls its digits like a counter as it follows the set-point.
 *
 * @param initial The set-point at first, in percent, and the one the self-playing demo rests on.
 * @param stops The labels of the scale from top to bottom. They are evenly spaced although the values are not.
 * @param extremes The labels that carry a yellow dot.
 * @param safe The recommended band: the line is cyan inside it.
 * @param autoPlay Moves the knob up and down by itself, in a loop.
 * @param onChange Called with the set-point, in whole percent, each time it changes.
 */
@Composable
fun BendingLineHumiditySlider(
    initial: Float = 35f,
    stops: List<Float> = listOf(100f, 75f, 50f, 45f, 40f, 35f, 30f, 25f, 10f, 0f),
    extremes: Set<Float> = setOf(100f, 75f, 50f, 25f, 10f, 0f),
    safe: ClosedFloatingPointRange<Float> = 20f..55f,
    labels: HumidityLabels = HumidityLabels(),
    autoPlay: Boolean = false,
    onChange: ((value: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still && stops.size > 1
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val ruler = remember(stops) { Scale(stops) }
    val motion = remember(ruler, initial) { SliderMotion(ruler.yOf(initial), initial.roundToInt()) }
    val changed by rememberUpdatedState(onChange)
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    // Cyan inside the recommended band, through violet to magenta outside it.
    fun tone(value: Float): Color {
        val over = max((value - safe.endInclusive) / BLEND_ABOVE, (safe.start - value) / BLEND_BELOW)
        return lerp(Cyan, Magenta, clamp01(over))
    }

    // The knob is pressed or let go: held, it shrinks a little under a grey ring; released, it bounces back.
    fun hold(down: Boolean) {
        scope.launch {
            if (still) motion.press.snapTo(if (down) 1f else 0f)
            else motion.press.animateTo(if (down) 1f else 0f, tween(((if (down) PRESS_TIME else RELEASE_TIME) * 1000).toInt(), easing = LinearEasing))
        }
        if (still) return
        scope.launch {
            if (down) motion.size.animateTo(PRESS_SCALE, tween((PRESS_TIME * 1000).toInt(), easing = EaseOutStrong))
            else motion.size.animateTo(1f, spring(dampingRatio = 13f / (2f * sqrt(420f)), stiffness = 420f, visibilityThreshold = 0.0005f))
        }
    }

    // Sends the knob to a set-point: over `time` seconds (`settle` runs a little past and back), or on a spring when `time` is 0.
    fun move(value: Float, time: Float = 0f, settle: Boolean = false) {
        val to = ruler.yOf(value)
        scope.launch {
            if (still) motion.knob.snapTo(to)
            else if (time > 0f) motion.knob.animateTo(to, tween((time * 1000).toInt(), easing = if (settle) SettleCurve else EaseInOut))
            else motion.knob.animateTo(to, spring(dampingRatio = DAMPING / (2f * sqrt(STIFFNESS)), stiffness = STIFFNESS, visibilityThreshold = 0.01f))
        }
    }

    // Each whole percent ticks, and leaving the recommended band sets off the warning.
    LaunchedEffect(motion, ruler) {
        snapshotFlow { ruler.valueAt(motion.knob.value).roundToInt() }.collect { now ->
            val before = motion.whole
            if (now == before) return@collect
            motion.whole = now
            if (motion.felt) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            changed?.invoke(now)
            if (before.toFloat() in safe && now.toFloat() !in safe && !still) {
                launch {
                    motion.warn.snapTo(0f)
                    motion.warn.animateTo(1f, tween((WARN_TIME * 1000).toInt(), easing = LinearEasing))
                }
            }
        }
    }

    // The bump's ends and the large figure catch up with the knob.
    LaunchedEffect(motion, ruler, still) {
        var before = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - before) / 1e9f, 1f / 30f)
            before = frame
            val knob = motion.knob.value
            val goal = ruler.valueAt(knob).roundToInt().toFloat()
            if (still) {
                motion.lag = knob
                motion.shown = goal
            } else {
                motion.lagSpeed += (LAG_STIFFNESS * (knob - motion.lag) - LAG_DAMPING * motion.lagSpeed) * dt
                motion.lag += motion.lagSpeed * dt
                val left = goal - motion.shown
                motion.shown = if (abs(left) < 0.01f) goal else motion.shown + left * (1f - exp(-dt / ROLL_TIME))
            }
        }
    }

    // The script a finger would follow: up, down past three labels, down into the magenta, and back.
    LaunchedEffect(auto, touches, motion) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, glide back to the resting set-point, and start over from there.
            delay((RESUME_AFTER * 1000).toLong())
            if (motion.held) return@LaunchedEffect
            motion.felt = false
            move(initial, HOME_TIME)
            delay(((HOME_TIME + 0.6f) * 1000).toLong())
        }
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
                move(step.to ?: initial, step.time, settle = step.to == null)
                until(step.at + step.time)
                hold(false)
            }
            turn += 1
        }
    }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .semantics {
                contentDescription = labels.humidityTitle
                stateDescription = "${motion.whole}%"
            }
            .pointerInput(motion, ruler) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    touches += 1
                    motion.held = true
                    motion.felt = true
                    scope.launch { motion.knob.stop() }
                    hold(true)
                    val from = motion.knob.value
                    var y = from
                    var dragged = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val moved = (change.position.y - down.position.y) / density
                        if (!dragged && abs(moved) > TAP_SLOP) dragged = true
                        if (dragged) {
                            change.consume()
                            y = ruler.clamped(from + moved)
                            scope.launch { motion.knob.snapTo(y) }
                        }
                    }
                    touches += 1
                    motion.held = false
                    hold(false)
                    // A tap on the scale sends the knob there; either way it comes to rest on a whole percent.
                    if (!dragged && down.position.x / density < READ_X - 10f) y = ruler.clamped(down.position.y / density)
                    move(ruler.valueAt(y).roundToInt().toFloat())
                }
            },
    ) {
        // The line with its bump, the ladder of ticks, the dots of the extreme labels and the knob.
        Canvas(Modifier.fillMaxSize()) {
            val y = motion.knob.value
            val lean = (motion.lag - y).coerceIn(-MAX_LEAN, MAX_LEAN)
            scale(density, density, pivot = Offset.Zero) {
                // Ticks every tenth of a step, a longer one every fifth; those beside the bump are pushed left with it.
                for (n in 0..(stops.size - 1) * 10) {
                    val at = SCALE_TOP + n * ruler.gap / 10f
                    val push = bend(at, y, lean)
                    val right = AXIS - TICK_INSET - push * BUMP
                    drawLine(lerp(Tick, TickLit, push), Offset(right - if (n % 5 == 0) 12f else 6f, at), Offset(right, at), strokeWidth = 1f)
                }
                stops.forEachIndexed { k, stop ->
                    if (stop in extremes) drawCircle(Yellow, 1.5f, Offset(DOT_X, SCALE_TOP + k * ruler.gap))
                }

                val line = Path().apply {
                    moveTo(AXIS - bend(0f, y, lean) * BUMP, 0f)
                    var at = 0f
                    while (at < HEIGHT) {
                        at = min(at + 2f, HEIGHT)
                        lineTo(AXIS - bend(at, y, lean) * BUMP, at)
                    }
                }
                // The line's colour belongs to the height, so the bump takes the colour of where it is; both ends fade out.
                val count = 48
                val colours = Array(count + 1) { n ->
                    val u = n / count.toFloat()
                    u to tone(ruler.valueAt(u * HEIGHT)).copy(alpha = clamp01(min(u, 1f - u) * HEIGHT / END_FADE))
                }
                drawPath(line, Brush.verticalGradient(*colours, startY = 0f, endY = HEIGHT), style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // The knob: a white bead with a pair of chevrons; held, it shows a grey ring and the chevrons dim.
                val press = motion.press.value
                withTransform({
                    translate(AXIS + KNOB_OUT, y)
                    scale(motion.size.value, motion.size.value, pivot = Offset.Zero)
                }) {
                    drawCircle(
                        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), center = Offset.Zero, radius = 30f),
                        radius = 30f, center = Offset.Zero,
                    )
                    drawCircle(Color.White, 17f, Offset.Zero)
                    drawCircle(Ring, 12.5f, Offset.Zero, alpha = press, style = Stroke(width = 2f))
                    val chevrons = Path().apply {
                        moveTo(-3.5f, -2.5f)
                        lineTo(0f, -6f)
                        lineTo(3.5f, -2.5f)
                        moveTo(-3.5f, 2.5f)
                        lineTo(0f, 6f)
                        lineTo(3.5f, 2.5f)
                    }
                    drawPath(chevrons, Night, alpha = 1f - 0.6f * press, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }

        stops.forEachIndexed { k, stop ->
            val at = SCALE_TOP + k * ruler.gap
            // A label the knob comes close to makes room for the live one.
            At(
                "${stop.roundToInt()}%", StopStyle, LABEL_X, at - 10f, 60f, 20f,
                Modifier.graphicsLayer { alpha = clamp01((abs(at - motion.knob.value) - HIDE_WITHIN) / HIDE_FADE) },
            )
        }
        BasicText(
            "${motion.whole}%",
            Modifier
                .offset { IntOffset(((LABEL_X - 2f) * density).roundToInt(), ((motion.knob.value - 12f) * density).roundToInt()) }
                .size(70.dp, 24.dp)
                .wrapContentHeight(),
            style = LiveStyle,
            maxLines = 1,
            color = { tone(ruler.valueAt(motion.knob.value)) },
        )

        At(labels.temperatureTitle, CaptionStyle, READ_X, 158f, 150f, 14f)
        At(labels.temperature, ValueStyle, READ_X, 176f, 150f, 26f)
        At(labels.humidityTitle, CaptionStyle, READ_X, 238f, 150f, 14f)
        // The large figure as a counter. Columns that would show a leading zero are left out.
        val columns by remember(motion) { derivedStateOf { if (motion.shown > 99f) 3 else if (motion.shown > 9f) 2 else 1 } }
        Row(Modifier.offset((READ_X - 2f).dp, 254.dp).height(DIGIT_H.dp)) {
            for (column in columns - 1 downTo 0) Strip(column, motion)
            BasicText("%", Modifier.padding(start = 1.dp).height(DIGIT_H.dp).wrapContentHeight(), style = DigitStyle, maxLines = 1)
        }
        At(labels.absoluteTitle, CaptionStyle, READ_X, 338f, 150f, 14f)
        At(labels.absolute, ValueStyle, READ_X, 356f, 150f, 26f)

        Image(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            modifier = Modifier
                .offset((READ_X - 2f).dp, 410.dp)
                .size(22.dp)
                .padding(2.dp)
                .graphicsLayer {
                    val swell = 1f + 0.25f * sin(Math.PI.toFloat() * motion.warn.value)
                    scaleX = swell
                    scaleY = swell
                },
            colorFilter = ColorFilter.tint(Yellow),
        )
        BasicText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Yellow)) { append("• ") }
                append(labels.warning)
            },
            Modifier.offset(READ_X.dp, 438.dp).width(148.dp),
            style = NoteStyle,
            maxLines = 3,
            color = { lerp(NoteInk, Yellow, sin(Math.PI.toFloat() * motion.warn.value)) },
        )
    }
}

/** One digit of the large figure: a strip of numerals that rolls behind a window. A strip only turns while the one to its right goes from 9 to 0. */
@Composable
private fun Strip(column: Int, motion: SliderMotion) {
    Box(Modifier.size(DIGIT_W.dp, DIGIT_H.dp).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    val value = motion.shown
                    var unit = 1f
                    repeat(column) { unit *= 10f }
                    val position = floor(value / unit) + if (column == 0) value % 1f else max(0f, value % unit - (unit - 1f))
                    translationY = -(position % 10f) * DIGIT_H * density
                },
        ) {
            for (row in 0..10) {
                BasicText("${row % 10}", Modifier.size(DIGIT_W.dp, DIGIT_H.dp).wrapContentSize(), style = DigitStyle, maxLines = 1)
            }
        }
    }
}

/** Text in a box whose top-left corner is at [x], [y], centred vertically. */
@Composable
private fun BoxScope.At(text: String, style: TextStyle, x: Float, y: Float, width: Float, height: Float, modifier: Modifier = Modifier) {
    BasicText(text, modifier.offset(x.dp, y.dp).size(width.dp, height.dp).wrapContentHeight(), style = style, maxLines = 1)
}
