package com.ngocdevv.nativecook.blocks.pickers.sleeprangescale

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowCircleUp
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The fixed words on the sheet. */
data class SleepLabels(
    val title: String = "sleep",
    val subtitle: String = "timings",
    val question: String = "when did you sleep last night?",
    val sleep: String = "Sleep time",
    val wake: String = "Wake time",
    val to24: String = "Switch to 24h",
    val to12: String = "Switch to 12h",
    val submit: String = "Update sleep",
)

private const val WIDTH = 340f
private const val HEIGHT = 384f
private const val CORNER = 32f

private const val HEADER_Y = 42f

// The read-outs' centre, and how far each is from the middle of the sheet.
private const val READOUT_Y = 128f
private const val COLUMN = 76f
private const val READOUT_WIDTH = 130f
private const val ROLL = 26f
private const val FOOTER_Y = 351f

// The scale: 24 hours of `PITCH` dp from `SCALE_LEFT`, the ticks centred on `TICK_Y`.
private const val SCALE_LEFT = 32f
private const val PITCH = 11.5f
private const val TICK_Y = 222f
private const val MINOR_TICK = 12f
private const val MAJOR_TICK = 20f

// The handles and the band, measured from the ticks' centre.
private const val HANDLE_TOP = -13f
private const val HANDLE_HEIGHT = 72f
private const val HANDLE_WIDTH = 4f
private const val BAND_TOP = 10f
private const val BAND_HEIGHT = 30f

// A drag that starts within this many dp of a handle takes it.
private const val CATCH_DISTANCE = 14f

// The drag's box starts this far left of the scale.
private const val TOUCH_INSET = 24f

private const val HOLD_TIME = 0.12f
private const val ROLL_TIME = 0.15f
private const val FORMAT_TIME = 0.25f

private const val LOOP = 9f

// After a touch the loop waits this long, returns to the first times, and starts over once they have settled.
private const val RESUME_AFTER = 2f
private const val SETTLE = 0.8f

private val StepSpring = spring<Float>(dampingRatio = 34f / (2f * sqrt(600f)), stiffness = 600f, visibilityThreshold = 0.001f)
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private val Ink = Color(0xFF2B2F36)
private val Hairline = Color(0xFF2B2F36).copy(alpha = 0.1f)
private val Mute = Color(0xFF8A8F98)
private val Faint = Color(0xFF9A9FA8)

private enum class Part { Sleep, Wake, Band }

private sealed interface Step {
    data class Grab(val part: Part) : Step
    data class Shift(val hours: Int) : Step
    data object Release : Step
    data object Format : Step
}

// autoPlay: what happens when, in seconds from the start of a loop. A shift counts hours from where
// the held part was taken.
private val Script: List<Pair<Float, Step>> = listOf(
    1.0f to Step.Grab(Part.Wake), 1.15f to Step.Shift(-1), 1.4f to Step.Shift(-2), 1.7f to Step.Release,
    2.3f to Step.Grab(Part.Sleep), 2.45f to Step.Shift(1), 2.7f to Step.Shift(2), 3.0f to Step.Release,
    3.6f to Step.Grab(Part.Band), 3.75f to Step.Shift(1), 4.0f to Step.Shift(2), 4.25f to Step.Shift(3), 4.55f to Step.Release,
    5.15f to Step.Grab(Part.Sleep), 5.3f to Step.Shift(-1), 5.45f to Step.Shift(-2), 5.6f to Step.Shift(-3),
    5.75f to Step.Shift(-4), 5.9f to Step.Shift(-5), 6.15f to Step.Release,
    6.5f to Step.Grab(Part.Wake), 6.65f to Step.Shift(-1), 6.9f to Step.Release,
    7.4f to Step.Format, 8.3f to Step.Format,
)

private fun wrap(hour: Int) = ((hour % 24) + 24) % 24

/** An hour of the day as a number and "AM" or "PM". */
private fun twelve(hour: Int) = (if (hour % 12 == 0) 12 else hour % 12).toString() to (if (hour < 12) "AM" else "PM")

private fun twentyFour(hour: Int) = (if (hour < 10) "0" else "") + "$hour:00"

private fun type(size: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = type(20f, FontWeight.Bold, Ink)
private val SubtitleStyle = type(9f, FontWeight.Normal, Mute)
private val QuestionStyle = type(11f, FontWeight.SemiBold, Color(0xFF6B7078))
private val CaptionStyle = type(9f, FontWeight.Medium, Mute)
private val NumberStyle = type(34f, FontWeight.ExtraBold, Ink)
private val HalfStyle = type(14f, FontWeight.Medium, Ink)
private val ClockStyle = type(30f, FontWeight.ExtraBold, Ink)
private val SpanStyle = type(11f, FontWeight.Medium, Mute)
private val MarkStyle = type(9f, FontWeight.Medium, Faint)
private val ChipStyle = type(11f, FontWeight.SemiBold, Color(0xFF3A4048))
private val SubmitStyle = type(12f, FontWeight.Bold, Color.White)

/** What moves. Positions count hours from the scale's left end. */
private class ScaleMotion(sleep: Int, wake: Int, is24: Boolean) {
    var sleep by mutableIntStateOf(sleep)
    var wake by mutableIntStateOf(wake)
    var is24 by mutableStateOf(is24)

    /** Where the two handles are drawn. */
    val sleepAt = Animatable(sleep.toFloat())
    val wakeAt = Animatable(wake.toFloat())

    /** What the two read-outs show: between two hours they are part-way through a roll. */
    val sleepShown = Animatable(sleep.toFloat())
    val wakeShown = Animatable(wake.toFloat())

    /** 0 at rest … 1 held, for each handle. */
    val sleepHeld = Animatable(0f)
    val wakeHeld = Animatable(0f)

    /** 0 twelve-hour times … 1 twenty-four-hour times. */
    val format = Animatable(if (is24) 1f else 0f)
    val chipPress = Animatable(0f)
    val submitPress = Animatable(0f)

    var held: Part? = null

    /** The hours when the held part was taken, and whether the finger has moved a step since. */
    var fromSleep = sleep
    var fromWake = wake
    var moved = false
}

/**
 * A sheet that asks when you slept. A scale of 24 hours, one tick an hour, carries two tall
 * handles with a band of colour between them: dragging a handle moves the sleep or the wake time
 * hour by hour, dragging the band moves both, and the two large read-outs above roll like an
 * odometer with every step. A chip switches the read-outs and the axis between 12 and 24 hours.
 *
 * @param startHour The hour of the day at the left end of the scale, 0…23.
 * @param sleep The first sleep time, as an hour of the day.
 * @param wake The first wake time, as an hour of the day.
 * @param use24Hours Shows 24-hour times at first.
 * @param autoPlay Moves the handles and the band by itself, in a loop.
 * @param onChange Called with the sleep and the wake hour (0…23) after every step.
 * @param onSubmit Called with both hours when the button is tapped.
 */
@Composable
fun SleepRangeScale(
    startHour: Int = 20,
    sleep: Int = 23,
    wake: Int = 7,
    labels: SleepLabels = SleepLabels(),
    use24Hours: Boolean = false,
    autoPlay: Boolean = false,
    onChange: ((sleep: Int, wake: Int) -> Unit)? = null,
    onSubmit: ((sleep: Int, wake: Int) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onChange)

    // The first positions on the scale: sleep 0…23, wake after it and at most 24.
    val firstSleep = wrap(sleep - startHour)
    val wakeOffset = wrap(wake - startHour)
    val firstWake = if (wakeOffset > firstSleep) wakeOffset else min(24, max(firstSleep + 1, if (wakeOffset == 0) 24 else wakeOffset))
    val motion = remember(firstSleep, firstWake, use24Hours) { ScaleMotion(firstSleep, firstWake, use24Hours) }
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }

    // Under reduced motion nothing travels: every change is a jump.
    fun move(value: Animatable<Float, AnimationVector1D>, target: Float, spec: AnimationSpec<Float>) {
        scope.launch { if (still) value.snapTo(target) else value.animateTo(target, spec) }
    }

    // Up in 80 ms, a 40 ms hold, back in 180 ms: a tap.
    fun pulse(value: Animatable<Float, AnimationVector1D>) {
        scope.launch {
            value.animateTo(1f, tween(80, easing = LinearEasing))
            delay(40)
            value.animateTo(0f, tween(180, easing = LinearEasing))
        }
    }

    fun touch() {
        touches += 1
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    // Takes hold of a handle or of the band, or lets go: what is held thickens.
    fun hold(part: Part?) {
        if (part != null) {
            motion.fromSleep = motion.sleep
            motion.fromWake = motion.wake
        }
        motion.held = part
        val spec = tween<Float>((HOLD_TIME * 1000).toInt(), easing = EaseOutStrong)
        move(motion.sleepHeld, if (part == Part.Sleep || part == Part.Band) 1f else 0f, spec)
        move(motion.wakeHeld, if (part == Part.Wake || part == Part.Band) 1f else 0f, spec)
    }

    fun set(nextSleep: Int, nextWake: Int, tick: Boolean) {
        if (nextSleep == motion.sleep && nextWake == motion.wake) return
        val roll = tween<Float>((ROLL_TIME * 1000).toInt(), easing = EaseOutStrong)
        if (nextSleep != motion.sleep) {
            motion.sleep = nextSleep
            move(motion.sleepAt, nextSleep.toFloat(), StepSpring)
            move(motion.sleepShown, nextSleep.toFloat(), roll)
        }
        if (nextWake != motion.wake) {
            motion.wake = nextWake
            move(motion.wakeAt, nextWake.toFloat(), StepSpring)
            move(motion.wakeShown, nextWake.toFloat(), roll)
        }
        if (tick) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        changed?.invoke(wrap(startHour + nextSleep), wrap(startHour + nextWake))
    }

    // Moves what is held to `hours` from where it was taken, as far as the scale and the other handle allow.
    fun shift(hours: Int, tick: Boolean) {
        val held = motion.held ?: return
        var nextSleep = motion.fromSleep
        var nextWake = motion.fromWake
        when (held) {
            Part.Sleep -> nextSleep = (nextSleep + hours).coerceIn(0, nextWake - 1)
            Part.Wake -> nextWake = (nextWake + hours).coerceIn(nextSleep + 1, 24)
            Part.Band -> {
                val along = hours.coerceIn(-nextSleep, 24 - nextWake)
                nextSleep += along
                nextWake += along
            }
        }
        set(nextSleep, nextWake, tick)
    }

    fun toggleFormat() {
        motion.is24 = !motion.is24
        pulse(motion.chipPress)
        move(motion.format, if (motion.is24) 1f else 0f, tween((FORMAT_TIME * 1000).toInt(), easing = EaseInOut))
    }

    // The script a finger would follow: the wake handle in, the sleep handle in, the band along, both
    // handles back, and the chip there and back.
    LaunchedEffect(auto, touches, motion) {
        if (!auto) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, go back to the first times, and start over once they have settled.
            delay((RESUME_AFTER * 1000).toLong())
            if (motion.held != null) return@LaunchedEffect
            set(firstSleep, firstWake, tick = false)
            if (motion.is24 != use24Hours) toggleFormat()
            delay((SETTLE * 1000).toLong())
        }
        // Every step is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        while (true) {
            for ((at, step) in Script) {
                val due = begin + ((turn * LOOP + at) * 1e9).toLong()
                delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                when (step) {
                    is Step.Grab -> hold(step.part)
                    is Step.Shift -> shift(step.hours, tick = false)
                    Step.Release -> hold(null)
                    Step.Format -> toggleFormat()
                }
            }
            turn += 1
        }
    }

    val shadow = remember { SoftRoundRect(Color(0xFF1C1B33).copy(alpha = 0.18f), sigma = 22f) }
    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .drawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, 12f, WIDTH, HEIGHT, CORNER) }
                }
            }
            .clip(RoundedCornerShape(CORNER.dp))
            .background(
                Brush.linearGradient(
                    0f to Color(0xFFF1EDFB),
                    0.35f to Color(0xFFFBFBFE),
                    0.7f to Color(0xFFE9F2FB),
                    1f to Color(0xFFF7EAF2),
                ),
            ),
    ) {
        // The grabber, the hairlines, the ticks, the band and the two handles, drawn in dp.
        Canvas(Modifier.fillMaxSize()) {
            scale(density, density, pivot = Offset.Zero) {
                drawRoundRect(Color(0xFFB9BCC6), Offset(WIDTH / 2f - 18f, 9f), Size(36f, 4f), CornerRadius(2f))
                drawRect(Hairline, Offset(20f, HEADER_Y + 28.5f), Size(WIDTH - 40f, 1f))
                drawRect(Hairline, Offset(20f, FOOTER_Y - 34.5f), Size(WIDTH - 40f, 1f))
                drawRect(Hairline, Offset(WIDTH / 2f - 0.5f, READOUT_Y + 8f - 22f), Size(1f, 44f))
                drawRect(Ink.copy(alpha = 0.45f), Offset(83.5f, HEADER_Y - 11f), Size(1f, 22f))

                for (hour in 0..24) {
                    val height = if (hour % 4 == 0) MAJOR_TICK else MINOR_TICK
                    drawRect(Color(0xFFB8BCC6), Offset(SCALE_LEFT + hour * PITCH - 0.5f, TICK_Y - height / 2f), Size(1f, height))
                }

                val left = SCALE_LEFT + motion.sleepAt.value * PITCH
                val right = SCALE_LEFT + motion.wakeAt.value * PITCH
                if (right > left) {
                    drawRect(
                        Brush.horizontalGradient(listOf(Color(0xFFA9B8FF), Color(0xFFC9A5F5)), startX = left, endX = right),
                        Offset(left, TICK_Y + BAND_TOP),
                        Size(right - left, BAND_HEIGHT),
                    )
                }
                for ((x, held) in listOf(left to motion.sleepHeld.value, right to motion.wakeHeld.value)) {
                    val width = HANDLE_WIDTH + 2f * held
                    val grow = 3f * held
                    drawRoundRect(
                        Ink,
                        Offset(x - width / 2f, TICK_Y + HANDLE_TOP - grow),
                        Size(width, HANDLE_HEIGHT + grow * 2f),
                        CornerRadius(width / 2f),
                    )
                }
            }
        }

        At(labels.title, TitleStyle, 20f, HEADER_Y - 19f, 60f, 24f)
        At(labels.subtitle, SubtitleStyle, 27f, HEADER_Y + 4f, 53f, 12f)
        At(labels.question, QuestionStyle, 98f, HEADER_Y - 10f, 190f, 20f)
        Box(
            Modifier
                .offset((WIDTH - 51f).dp, (HEADER_Y - 20f).dp)
                .size(40.dp)
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    touch()
                    onClose?.invoke()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Cancel, contentDescription = "Close", tint = Ink, modifier = Modifier.size(26.dp))
        }

        Readout(Icons.Rounded.Bed, labels.sleep, WIDTH / 2f - COLUMN, startHour, { motion.sleepShown.value }, { motion.format.value })
        Readout(Icons.Rounded.Alarm, labels.wake, WIDTH / 2f + COLUMN, startHour, { motion.wakeShown.value }, { motion.format.value })
        val span = motion.wake - motion.sleep
        At("~$span ${if (span == 1) "hour" else "hours"} of sleep", SpanStyle, 0f, READOUT_Y + 49f, WIDTH, 16f, Alignment.Center)

        for (mark in 0..24 step 4) {
            val hour = wrap(startHour + mark)
            val x = SCALE_LEFT + mark * PITCH - 20f
            Box(Modifier.graphicsLayer { alpha = 1f - motion.format.value }) {
                At(twelve(hour).let { "${it.first} ${it.second}" }, MarkStyle, x, TICK_Y + 67f, 40f, 14f, Alignment.Center)
            }
            Box(Modifier.graphicsLayer { alpha = motion.format.value }) {
                At(twentyFour(hour), MarkStyle, x, TICK_Y + 67f, 40f, 14f, Alignment.Center)
            }
        }

        // One drag over the scale. It takes the handle it starts near, or the band when it starts
        // between the handles; a tap moves the nearer handle to the tapped hour.
        Box(
            Modifier
                .offset((SCALE_LEFT - TOUCH_INSET).dp, (TICK_Y + 26f - 48f).dp)
                .size((PITCH * 24f + TOUCH_INSET * 2f).dp, 96.dp)
                .semantics { contentDescription = "${labels.sleep} ${twentyFour(wrap(startHour + motion.sleep))}, ${labels.wake} ${twentyFour(wrap(startHour + motion.wake))}" }
                .pointerInput(motion) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val at = (down.position.x / density - TOUCH_INSET) / PITCH
                        val toSleep = abs(at - motion.sleep)
                        val toWake = abs(at - motion.wake)
                        val near = min(toSleep, toWake) * PITCH <= CATCH_DISTANCE
                        val inside = at > motion.sleep && at < motion.wake
                        motion.moved = false
                        hold(if (inside && !near) Part.Band else if (toSleep <= toWake) Part.Sleep else Part.Wake)
                        touch()
                        var travel = 0f
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            travel = (change.position.x - down.position.x) / density
                            val hours = (travel / PITCH).roundToInt()
                            if (hours != 0) motion.moved = true
                            shift(hours, tick = true)
                        }
                        if (!motion.moved && motion.held != Part.Band && abs(travel) < 4f) {
                            val hour = at.roundToInt()
                            shift(hour - (if (motion.held == Part.Sleep) motion.fromSleep else motion.fromWake), tick = true)
                        }
                        hold(null)
                        touch()
                    }
                },
        )

        Box(
            Modifier
                .offset(20.dp, (FOOTER_Y - 15f).dp)
                .size(104.dp, 30.dp)
                .graphicsLayer {
                    val dip = 1f - 0.05f * motion.chipPress.value
                    scaleX = dip
                    scaleY = dip
                }
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFECEDEF))
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    touch()
                    toggleFormat()
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(if (motion.is24) labels.to12 else labels.to24, style = ChipStyle, maxLines = 1)
        }

        Row(
            Modifier
                .offset((WIDTH - 160f).dp, (FOOTER_Y - 21f).dp)
                .size(140.dp, 42.dp)
                .graphicsLayer {
                    val dip = 1f - 0.04f * motion.submitPress.value
                    scaleX = dip
                    scaleY = dip
                }
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF3A4048))
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    touch()
                    pulse(motion.submitPress)
                    onSubmit?.invoke(wrap(startHour + motion.sleep), wrap(startHour + motion.wake))
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ArrowCircleUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            BasicText(labels.submit, style = SubmitStyle, maxLines = 1)
        }
    }
}

/** A line of text in a box whose top-left corner is at [x], [y]. */
@Composable
private fun BoxScope.At(text: String, style: TextStyle, x: Float, y: Float, width: Float, height: Float, align: Alignment = Alignment.CenterStart) {
    Box(Modifier.offset(x.dp, y.dp).size(width.dp, height.dp), contentAlignment = align) {
        BasicText(text, style = style, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentHeight(unbounded = true))
    }
}

/** An icon, a label and the time, which is a drum of two slots. [x] is the read-out's centre. */
@Composable
private fun BoxScope.Readout(icon: ImageVector, label: String, x: Float, startHour: Int, value: () -> Float, format: () -> Float) {
    Box(Modifier.offset((x - READOUT_WIDTH / 2f).dp, (READOUT_Y - 50f).dp).size(READOUT_WIDTH.dp, 100.dp)) {
        Icon(icon, contentDescription = null, tint = Faint, modifier = Modifier.align(Alignment.TopCenter).size(22.dp))
        At(label, CaptionStyle, 0f, 24f, READOUT_WIDTH, 12f, Alignment.Center)
        Slot(0, startHour, value, format)
        Slot(1, startHour, value, format)
    }
}

/**
 * One slot of a read-out's drum. It shows the hour of its [parity] (0 even, 1 odd) nearest to
 * [value]: at that hour it is in place, and as [value] moves on it slides up (or down, going back)
 * and fades while the other slot arrives. Its text only changes while it is out of sight.
 */
@Composable
private fun Slot(parity: Int, startHour: Int, value: () -> Float, format: () -> Float) {
    fun nearest() = 2 * ((value() - parity) / 2f).roundToInt() + parity
    val shown by remember(parity) { derivedStateOf { nearest() } }
    val hour = wrap(startHour + shown.coerceIn(0, 24))
    val (number, half) = twelve(hour)
    Box(
        Modifier
            .offset(0.dp, 40.dp)
            .size(READOUT_WIDTH.dp, 40.dp)
            .graphicsLayer {
                val away = value() - nearest()
                alpha = 1f - min(1f, abs(away))
                translationY = (-away * ROLL).dp.toPx()
            },
    ) {
        Row(
            Modifier.fillMaxSize().wrapContentSize(unbounded = true).graphicsLayer { alpha = 1f - format() },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(number, style = NumberStyle, maxLines = 1, modifier = Modifier.alignByBaseline())
            BasicText(half, style = HalfStyle, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
        BasicText(
            twentyFour(hour),
            style = ClockStyle,
            maxLines = 1,
            modifier = Modifier.fillMaxSize().wrapContentSize(unbounded = true).graphicsLayer { alpha = format() },
        )
    }
}
