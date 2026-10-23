package com.ngocdevv.nativecook.blocks.pickers.datelensstrip

import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * One day of the strip, already in words.
 *
 * @param weekday "FRI"
 * @param number "06"
 * @param month "NOVEMBER": shown in place of the label for a moment when the lens crosses into this month.
 */
class LensDay(val weekday: String, val number: String, val month: String) {
    companion object {
        /** [count] days in a row, starting at the given date. */
        fun run(year: Int, month: Int, day: Int, count: Int): List<LensDay> {
            val first = LocalDate.of(year, month, day)
            return List(count) { offset ->
                val date = first.plusDays(offset.toLong())
                LensDay(
                    weekday = date.dayOfWeek.name.take(3),
                    number = date.dayOfMonth.toString().padStart(2, '0'),
                    month = date.month.name,
                )
            }
        }
    }
}

// Four weeks from Monday 26 October 2026; Friday 6 November is index 11.
private val Days = LensDay.run(2026, 10, 26, 28)

// The component's height: the lens and room for it to wobble.
private const val HEIGHT = 120f
private const val MARGIN = 16f
private const val BAR_H = 64f

// Measured from the bar's left end: where the label starts, the lens's centre, and the days' pitch.
private const val LABEL_X = 22f
private const val LENS_X = 214f
private const val PITCH = 62f
private const val LENS_W = 66f
private const val LENS_H = 90f

// How much wider and lower the lens gets at full stretch, and how far it leans.
private const val STRETCH_W = 12f
private const val SQUASH_H = 6f
private const val LEAN = 5f

// The strip's speed, in days per second, that stretches the lens all the way.
private const val FULL_SPEED = 12f

// The lens's spring (response 0.4 s, damping 0.6), stepped at most `TICK` seconds at a time.
private const val LENS_STIFFNESS = 247f
private const val LENS_DAMPING = 18.85f
private const val TICK = 1f / 240f

// A day is 1.12× under the lens and back to 1× one day away.
private const val GROW = 0.12f
private const val WEEKDAY_Y = 18f
private const val NUMBER_Y = 10f

// Days further than this from the lens are not drawn.
private const val REACH = 5f

// A day is gone at `FADE_FROM` from the bar's left end and whole `FADE_OVER` further; the same near the right end.
private const val FADE_FROM = 130f
private const val FADE_OVER = 22f
private const val END_FADE = 14f
private const val END_OVER = 34f

// How far the label travels as it rolls, and how long a month's name stays.
private const val ROLL_Y = 22f
private const val MONTH_TIME = 1.2f

// Dragging: the travel that makes a touch a drag, how far a flick carries, and the pull past the ends.
private const val SLOP = 8f
private const val GLIDE = 0.16f
private const val MAX_SPEED = 24f
private const val RUBBER = 0.35f

// autoPlay (seconds): when the strip sets off, and to which day, counted from the resting one.
private val Script = listOf(1.0f to 1, 2.25f to 5, 4.05f to 3, 5.35f to 0)
private const val LOOP = 7f

// After a touch the loop waits this long before it starts over.
private const val RESUME_AFTER = 2f

/** A spring of mass 1 with the stiffness and damping the other versions use. */
private fun physical(stiffness: Float, damping: Float) =
    spring(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness, visibilityThreshold = 0.0005f)

private val StripSpring = physical(180f, 17.5f)
private val RollSpring = physical(260f, 24f)

private val Royal = Color(0xFF1414C8)
private val Electric = Color(0xFF0000F5)
private val WeekdayColor = Color(0xFF8C8C8C)

private fun type(size: Float, weight: FontWeight, spacing: Float = 0f) = TextStyle(
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val LabelStyle = type(13f, FontWeight.Bold)
private val WeekdayStyle = type(9.5f, FontWeight.Medium, 0.3f)
private val NumberStyle = type(21f, FontWeight.Bold)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

/**
 * A date picker in a white capsule: a label on the left, and a strip of days that slides under a
 * fixed lens, a tall blue capsule that stands proud of the bar and shows the day beneath it in white.
 * Dragging moves the strip, and it settles on the nearest day; tapping a day brings it under the
 * lens. The lens is soft: it stretches and leans with the strip's speed and wobbles as it stops.
 *
 * @param initial Index of the day that starts under the lens.
 * @param autoPlay Slides the strip through a short script by itself, in a loop.
 * @param onSelect Called with the index of the day the strip is settling on.
 */
@Composable
fun DateLensStrip(
    days: List<LensDay> = Days,
    initial: Int = 11,
    label: String = "PICK A DATE:",
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val last = (days.size - 1).coerceAtLeast(0)
    val start = initial.coerceIn(0, last)
    val auto = autoPlay && !still && days.size > 1
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val chosen by rememberUpdatedState(onSelect)

    // The strip: the index under the lens, continuous while it slides.
    val strip = remember(days.size, start) { Animatable(start.toFloat()) }
    // The label's roll: 0 the label, 1 a month's name.
    val roll = remember { Animatable(0f) }
    var month by remember { mutableStateOf("") }
    // The lens's stretch, −1…1 and a little beyond: a spring that chases the strip's speed.
    var stretch by remember { mutableFloatStateOf(0f) }
    // `held`: a finger is on the strip. `byHand`: it slides under a finger, not under the script; only then it ticks.
    val hand = remember { object { var held = false; var byHand = false } }
    // Counts touches: each one stops the script and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    val measurer = rememberTextMeasurer()
    val title = remember(label, measurer) { measurer.measure(label, LabelStyle, maxLines = 1) }
    val name = remember(month, measurer) { measurer.measure("$month:", LabelStyle, maxLines = 1) }
    val weekdays = remember(days, measurer) { days.map { measurer.measure(it.weekday, WeekdayStyle, maxLines = 1) } }
    val numbers = remember(days, measurer) { days.map { measurer.measure(it.number, NumberStyle, maxLines = 1) } }
    val shade = remember { SoftRoundRect(Color.Black.copy(alpha = 0.1f), sigma = 12f) }

    // Sends the strip to a day, carrying on at `velocity` days per second.
    fun settle(target: Float, velocity: Float = 0f) {
        val day = target.roundToInt().coerceIn(0, last)
        scope.launch {
            // Under reduced motion nothing eases: the strip jumps.
            if (still) strip.snapTo(day.toFloat()) else strip.animateTo(day.toFloat(), StripSpring, initialVelocity = velocity)
        }
        if (days.isNotEmpty()) chosen?.invoke(day)
    }

    // Steps the lens's spring towards the strip's speed; small steps keep it steady when a frame is late.
    LaunchedEffect(strip, still) {
        if (still) return@LaunchedEffect
        var before = withFrameNanos { it }
        var seen = strip.value
        var speed = 0f
        while (true) {
            val frame = withFrameNanos { it }
            val passed = min((frame - before) / 1e9f, 0.25f)
            before = frame
            if (passed <= 0f) continue
            val pull = ((strip.value - seen) / passed / FULL_SPEED).coerceIn(-1f, 1f)
            seen = strip.value
            val steps = ceil(passed / TICK).toInt()
            val dt = passed / steps
            var at = stretch
            repeat(steps) {
                speed += (LENS_STIFFNESS * (pull - at) - LENS_DAMPING * speed) * dt
                at += speed * dt
            }
            if (pull == 0f && abs(at) < 0.001f && abs(speed) < 0.01f) {
                at = 0f
                speed = 0f
            }
            stretch = at
        }
    }

    // When another day comes nearest the lens it ticks, and the label rolls if the month changed.
    LaunchedEffect(strip, days, still) {
        var nearest = strip.value.roundToInt().coerceIn(0, last)
        var rolling: Job? = null
        snapshotFlow { strip.value.roundToInt().coerceIn(0, last) }.drop(1).collect { now ->
            if (days[now].month != days[nearest].month) {
                month = days[now].month
                rolling?.cancel()
                rolling = launch {
                    val up = launch { if (still) roll.snapTo(1f) else roll.animateTo(1f, RollSpring) }
                    delay((MONTH_TIME * 1000).toLong())
                    up.cancel()
                    if (still) roll.snapTo(0f) else roll.animateTo(0f, RollSpring)
                }
            }
            nearest = now
            if (hand.byHand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    // The script a finger would follow: four moves along the strip, the last one back to where it began.
    LaunchedEffect(auto, touches, strip) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, bring the first day back, and start over from rest.
            delay((RESUME_AFTER * 1000).toLong())
            if (hand.held) return@LaunchedEffect
        }
        hand.byHand = false
        settle(start.toFloat())
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        while (true) {
            for ((at, move) in Script) {
                val due = begin + ((turn * LOOP + at) * 1e9).toLong()
                delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                settle((start + move).toFloat())
            }
            turn += 1
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(HEIGHT.dp)
            .semantics {
                contentDescription = label
                if (days.isNotEmpty()) {
                    val day = days[strip.targetValue.roundToInt().coerceIn(0, last)]
                    stateDescription = "${day.weekday} ${day.number}"
                }
            }
            // One gesture for the whole bar: a touch catches the strip, a drag slides it and lets go
            // with momentum, and a touch that never moved brings the day under it to the lens.
            .pointerInput(strip, still) {
                val pitch = PITCH.dp.toPx()
                val slop = SLOP.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val from = strip.value
                    val tracker = VelocityTracker()
                    var zero: Float? = null
                    var here = from
                    scope.launch { strip.stop() }
                    hand.held = true
                    hand.byHand = true
                    touches += 1
                    tracker.addPosition(down.uptimeMillis, down.position)
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val travel = change.position.x - down.position.x
                            if (zero == null && abs(travel) > slop) zero = travel
                            val began = zero ?: continue
                            change.consume()
                            // Past either end the strip follows at a third of the finger's speed.
                            val free = from - (travel - began) / pitch
                            here = if (free < 0f) free * RUBBER else if (free > last) last + (free - last) * RUBBER else free
                            scope.launch { strip.snapTo(here) }
                        }
                    } finally {
                        if (zero == null) {
                            val x = down.position.x.toDp().value - MARGIN
                            val onDays = x > FADE_FROM && x < size.width.toDp().value - MARGIN * 2f &&
                                abs(down.position.y - size.height / 2f) < LENS_H.dp.toPx() / 2f
                            settle(if (onDays) strip.value + (x - LENS_X) / PITCH else strip.value)
                        } else {
                            val speed = (-tracker.calculateVelocity().x / pitch).coerceIn(-MAX_SPEED, MAX_SPEED)
                            settle(here + speed * GLIDE, speed)
                        }
                        hand.held = false
                        touches += 1
                    }
                }
            },
    ) {
        val d = density
        val at = strip.value
        val rolled = roll.value
        val pulled = if (still) 0f else stretch
        val left = MARGIN * d
        val width = size.width - MARGIN * 2f * d
        val middle = size.height / 2f
        val bar = Path().apply {
            addRoundRect(RoundRect(Rect(left, middle - BAR_H / 2f * d, left + width, middle + BAR_H / 2f * d), CornerRadius(BAR_H / 2f * d)))
        }

        scale(d, d, pivot = Offset.Zero) {
            drawIntoCanvas { shade.draw(it.nativeCanvas, MARGIN, middle / d - BAR_H / 2f + 8f, width / d, BAR_H, BAR_H / 2f) }
        }
        drawPath(bar, Color.White)

        clipPath(bar) {
            // The label rolls up and a month's name rolls in from below, then back.
            drawText(
                title, Royal,
                topLeft = Offset(left + LABEL_X * d, middle - ROLL_Y * rolled * d - title.size.height / 2f),
                alpha = 1f - clamp01(rolled),
            )
            if (rolled > 0f) {
                drawText(
                    name, Royal,
                    topLeft = Offset(left + LABEL_X * d, middle + ROLL_Y * (1f - rolled) * d - name.size.height / 2f),
                    alpha = clamp01(rolled),
                )
            }
            drawDays(weekdays, numbers, at, left, width, lit = false)
        }

        // The lens stretches sideways with the strip's speed, thins, and leans the way the days travel.
        val pull = min(abs(pulled), 1.15f)
        val lensW = (LENS_W + STRETCH_W * pull) * d
        val lensH = (LENS_H - SQUASH_H * pull) * d
        val lensLeft = left + (LENS_X - LEAN * pulled) * d - lensW / 2f
        val lens = Path().apply {
            addRoundRect(RoundRect(Rect(lensLeft, middle - lensH / 2f, lensLeft + lensW, middle + lensH / 2f), CornerRadius(lensW / 2f)))
        }
        drawPath(lens, Electric)
        // The same days again in white, cut at the lens's edge: a number half-way in is half white.
        clipPath(lens) { drawDays(weekdays, numbers, at, left, width, lit = true) }
    }
}

/** The days around the lens, each a weekday over a number, growing as it nears the lens. */
private fun DrawScope.drawDays(
    weekdays: List<TextLayoutResult>,
    numbers: List<TextLayoutResult>,
    strip: Float,
    left: Float,
    width: Float,
    lit: Boolean,
) {
    val d = density
    for (index in weekdays.indices) {
        val away = index - strip
        if (abs(away) >= REACH) continue
        val x = LENS_X + away * PITCH
        // Days fade as they slide under the label and into the bar's right end.
        val fade = if (lit) 1f else min(clamp01((x - FADE_FROM) / FADE_OVER), clamp01((width / d - END_FADE - x) / END_OVER))
        if (fade <= 0f) continue
        val weekday = weekdays[index]
        val number = numbers[index]
        withTransform({
            translate(left + x * d, size.height / 2f)
            scale(1f + GROW * clamp01(1f - abs(away)), pivot = Offset.Zero)
        }) {
            drawText(
                weekday, if (lit) Color.White else WeekdayColor,
                topLeft = Offset(-weekday.size.width / 2f, -WEEKDAY_Y * d - weekday.size.height / 2f),
                alpha = fade,
            )
            drawText(
                number, if (lit) Color.White else Color.Black,
                topLeft = Offset(-number.size.width / 2f, NUMBER_Y * d - number.size.height / 2f),
                alpha = fade,
            )
        }
    }
}
