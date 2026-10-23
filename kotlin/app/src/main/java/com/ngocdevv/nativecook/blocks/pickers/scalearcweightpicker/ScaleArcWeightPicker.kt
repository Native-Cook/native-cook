package com.ngocdevv.nativecook.blocks.pickers.scalearcweightpicker

import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

// The card, and the room for its shadow round it.
private const val WIDTH = 340f
private const val HEIGHT = 356f
private const val RADIUS = 32f
private const val BLEED = 44f
private const val MIDDLE = WIDTH / 2f
private const val TITLE_Y = 38f
private const val MESSAGE_Y = 67f

// The kg / lbs switch.
private const val SWITCH_Y = 110f
private const val SWITCH_W = 96f
private const val SWITCH_H = 28f

// The two boxes with today's weight and the target.
private const val BOX_Y = 162f
private const val BOX_W = 54f
private const val BOX_H = 48f
private const val BOX_GAP = 84f
private const val TRIANGLE_Y = 198f

// The dial is the top of a circle of `DIAL_R` whose highest point is at `DIAL_Y`.
private const val DIAL_Y = 214f
private const val DIAL_R = 380f
private const val CENTER_Y = DIAL_Y + DIAL_R
private const val BAND = 90f

// Degrees between two units, and the dp a finger travels for one unit.
private const val PITCH = 2.6f
private const val UNIT_ARC = 16.6f
private const val LONG_EVERY = 5
private const val POUND = 2.2046f

// The simulation's step, in seconds, and the longest a frame is counted as.
private const val TICK = 1f / 240f
private const val LONGEST_FRAME = 0.25f

private class Springy(val stiffness: Float, val damping: Float)

private val Settle = Springy(60f, 15f)

// A released dial rests this many seconds of its speed further on.
private const val COAST = 0.25f

// A held dial follows the finger this closely, in seconds.
private const val GRIP = 0.03f
private val Needle = Springy(180f, 11f)

// The needle leans up to `LEAN` degrees, `DRAG` degrees for each unit a second, and gets a push of
// `SWING` degrees a second when the dial comes to rest.
private const val LEAN = 1.2f
private const val DRAG = 0.06f
private const val SWING = 36f
private val Bob = Springy(500f, 22f)
private const val KNOCK = 60f
private val Pulse = Springy(300f, 20f)
private const val SWELL = 1.2f
private val Knob = Springy(320f, 26f)
private const val CONVERT_TIME = 0.7f

// autoPlay (seconds). Each turn: when it starts, the units from the first target, how long it takes.
private const val LOOP = 8f
private const val BACK_TIME = 0.8f

private class Turn(val at: Float, val offset: Int, val time: Float)

private val Script = listOf(Turn(1f, 6, 0.9f), Turn(2.7f, -6, 1.1f), Turn(4.5f, -13, 0.6f), Turn(5.9f, 0, 1f))

// After a touch the loop waits this long before it starts over.
private const val RESUME_AFTER = 2f

private val Ink = Color(0xFF1E1E1E)
private val Leaf = Color(0xFF18B63C)
private val Amber = Color(0xFFF5A623)
private val Steel = Color(0xFF9BA1A8)

// Text is measured at one pixel a dp and drawn on a canvas scaled to dp.
private val UnitDensity = Density(1f)

private fun type(size: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TitleStyle = type(17f, Ink, FontWeight.SemiBold)
private val MessageStyle = type(11f, Color(0xFF7A7F87))
private val UnitStyle = type(11f, Ink, FontWeight.Medium)
private val FigureStyle = type(18f, Ink, FontWeight.SemiBold)
private val GapStyle = type(13f, Leaf, FontWeight.SemiBold)
private val MarkStyle = type(13f, Color(0xFF6F757D))

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun smooth(t: Float) = t * t * (3f - 2f * t)

/** Cubic ease-in-out, for the dial's set courses. */
private fun inOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

private enum class Mode {
    /** A finger holds the dial. */
    Held,

    /** The dial follows a set course: the script, or the spin to the other unit. */
    Gliding,

    /** The dial coasts to the whole unit it will rest on. */
    Free,
}

/** Everything that moves, stepped in small fixed steps on every frame. */
private class Sim(current: Int, target: Int, private val kilograms: IntRange) {
    /** The weight under the needle and today's weight, in the unit shown. */
    var value = target.toFloat()
    var current = current.toFloat()
    var pounds = false

    /** The switch's knob: 0 on kg … 1 on lbs. */
    var knob = 0f

    /** The needle's lean, in degrees. */
    var needle = 0f

    /** The triangle's bob, in dp, and the swell of the target's box. */
    var bob = 0f
    var pulse = 0f

    /** The dial was last moved by a finger, not by the script. */
    var byHand = false
    var mode = Mode.Free

    private var goal = value
    private var speed = 0f

    // A course: where the target and today's weight start and end, and how far along it is.
    private var from = 0f
    private var to = 0f
    private var currentFrom = 0f
    private var currentTo = 0f
    private var elapsed = 0f
    private var duration = 0f

    /** The dial's place when the finger came down. */
    private var grabbed = 0f
    private var moving = false

    /** The way the dial last turned: 1 towards heavier, -1 towards lighter. */
    private var heading = 1f
    private var whole = target
    private var knobSpeed = 0f
    private var needleSpeed = 0f
    private var bobSpeed = 0f
    private var pulseSpeed = 0f

    private val low get() = if (pounds) round(kilograms.first * POUND) else kilograms.first.toFloat()
    private val high get() = if (pounds) round(kilograms.last * POUND) else kilograms.last.toFloat()

    fun grab() {
        mode = Mode.Held
        byHand = true
        grabbed = value
        goal = value
    }

    /** The finger has moved [distance] dp to the right since it came down: the ticks go with it. */
    fun drag(distance: Float) {
        if (mode == Mode.Held) goal = (grabbed - distance / UNIT_ARC).coerceIn(low, high)
    }

    /** The finger lifted at [velocity] dp a second: the dial coasts on and rests on a whole unit. */
    fun release(velocity: Float) {
        if (mode != Mode.Held) return
        mode = Mode.Free
        speed = -velocity / UNIT_ARC
        goal = round(value + speed * COAST).coerceIn(low, high)
        moving = true
    }

    /** One unit up or down. */
    fun nudge(units: Float) {
        mode = Mode.Free
        byHand = true
        goal = (round(goal) + units).coerceIn(low, high)
        moving = true
    }

    /** Sends the dial to [weight] by itself, easing in and out. */
    fun glide(weight: Float, time: Float) {
        byHand = false
        course(weight.coerceIn(low, high), current, time)
    }

    /** Shows the same weights in the other unit: the dial spins to the new numbers. */
    fun convert(toPounds: Boolean) {
        if (toPounds == pounds) return
        val factor = if (toPounds) POUND else 1f / POUND
        byHand = false
        pounds = toPounds
        course(round(round(goal) * factor).coerceIn(low, high), round(round(current) * factor), CONVERT_TIME)
    }

    private fun course(weight: Float, today: Float, time: Float) {
        mode = Mode.Gliding
        from = value
        to = weight
        currentFrom = current
        currentTo = today
        goal = weight
        elapsed = 0f
        duration = time
        moving = true
    }

    fun advance(passed: Float, still: Boolean) {
        if (passed <= 0f) return
        val steps = max(1, ceil(passed / TICK).toInt())
        repeat(steps) { move(passed / steps) }
        // Under reduced motion nothing turns: every change is a jump.
        if (still) settle()
    }

    private fun move(h: Float) {
        val before = value
        when (mode) {
            Mode.Held -> {
                // A touch closer than a frame apart, so the speed has no steps in it.
                value += (goal - value) * (1f - exp(-h / GRIP))
                speed = (value - before) / h
            }
            Mode.Gliding -> {
                elapsed += h
                val t = min(1f, elapsed / max(duration, 0.001f))
                val eased = inOut(t)
                value = from + (to - from) * eased
                current = currentFrom + (currentTo - currentFrom) * eased
                speed = (value - before) / h
                if (t >= 1f) {
                    mode = Mode.Free
                    speed = 0f
                }
            }
            Mode.Free -> {
                speed += (Settle.stiffness * (goal - value) - Settle.damping * speed) * h
                value += speed * h
            }
        }

        if (abs(speed) > 0.5f) heading = if (speed > 0f) 1f else -1f
        // Each unit that passes under the needle knocks the triangle.
        val unit = value.roundToInt()
        if (unit != whole) {
            whole = unit
            bobSpeed -= KNOCK
        }
        // At rest: the needle gets a push the way the dial was going, and the box swells.
        if (moving && mode == Mode.Free && abs(goal - value) < 0.02f && abs(speed) < 0.4f) {
            moving = false
            needleSpeed -= heading * SWING
            pulseSpeed += SWELL
        }

        // The needle is dragged along by the dial while it turns.
        val lean = (-speed * DRAG).coerceIn(-LEAN, LEAN)
        needleSpeed += (Needle.stiffness * (lean - needle) - Needle.damping * needleSpeed) * h
        needle += needleSpeed * h
        bobSpeed += (-Bob.stiffness * bob - Bob.damping * bobSpeed) * h
        bob = (bob + bobSpeed * h).coerceIn(-3f, 3f)
        pulseSpeed += (-Pulse.stiffness * pulse - Pulse.damping * pulseSpeed) * h
        pulse += pulseSpeed * h
        knobSpeed += (Knob.stiffness * ((if (pounds) 1f else 0f) - knob) - Knob.damping * knobSpeed) * h
        knob += knobSpeed * h
    }

    private fun settle() {
        if (mode == Mode.Gliding) {
            current = currentTo
            mode = Mode.Free
        }
        if (mode == Mode.Free) value = round(goal)
        speed = 0f
        moving = false
        whole = value.roundToInt()
        knob = if (pounds) 1f else 0f
        knobSpeed = 0f
        needle = 0f
        needleSpeed = 0f
        bob = 0f
        bobSpeed = 0f
        pulse = 0f
        pulseSpeed = 0f
    }
}

/**
 * A target-weight picker shaped like the window of a bathroom scale. A card holds a kg / lbs switch,
 * today's weight and the target in two boxes, and at the bottom the top of a large white dial with
 * ticks and numbers under a fixed green needle. Dragging the dial turns it with weight: it coasts,
 * settles on a whole unit, and the needle swings. A wash over the ticks spans from today's weight to
 * the target, green for a gain and amber for a loss.
 *
 * @param current Today's weight, in kilograms.
 * @param target The target at first, in kilograms; [autoPlay] comes back to it at the end of every loop.
 * @param range The lightest and the heaviest target the dial offers, in kilograms.
 * @param autoPlay Turns the dial to three targets and back by itself, in a loop.
 * @param onChange Called with the target and whether it is in pounds whenever it changes.
 */
@Composable
fun ScaleArcWeightPicker(
    title: String = "Your Target Weight",
    message: String = "Set a target weight to personalize your health\nand progress journey.",
    current: Int = 60,
    target: Int = 70,
    range: IntRange = 30..200,
    autoPlay: Boolean = false,
    onChange: ((value: Int, pounds: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val auto = autoPlay && !still
    val view = LocalView.current
    val changed by rememberUpdatedState(onChange)
    val measurer = rememberTextMeasurer(cacheSize = 64)

    val sim = remember(current, target, range) { Sim(current, target, range) }
    var reported by remember(sim) { mutableIntStateOf(target) }
    var pounds by remember(sim) { mutableStateOf(false) }
    // Counts frames, so the canvas is drawn again after each step.
    var frame by remember { mutableLongStateOf(0L) }
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }
    // A finger is on the dial.
    var holding by remember { mutableStateOf(false) }
    val cardShadow = remember { SoftRoundRect(Color(0xFF7A6FB0).copy(alpha = 0.22f), sigma = 22f) }
    val bandShadow = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFF6A5FA8).copy(alpha = 0.2f).toArgb()
            maskFilter = BlurMaskFilter(sigmaToRadius(12f), BlurMaskFilter.Blur.NORMAL)
        }
    }

    LaunchedEffect(sim, still) {
        var before = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            sim.advance(min((now - before) / 1e9f, LONGEST_FRAME), still)
            before = now
            val value = sim.value.roundToInt()
            if (value != reported || sim.pounds != pounds) {
                // A firmer tick at each long tick; none while the dial turns by itself.
                if (value != reported && sim.byHand && sim.mode != Mode.Gliding) {
                    view.performHapticFeedback(if (value % LONG_EVERY == 0) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK)
                }
                reported = value
                pounds = sim.pounds
                changed?.invoke(value, sim.pounds)
            }
            frame += 1
        }
    }

    // The script a finger would follow: up six units, down twelve, on below today's weight, and back.
    LaunchedEffect(auto, touches, holding, sim) {
        if (!auto || holding) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, go back to kilograms and the first target, and start over.
            delay((RESUME_AFTER * 1000).toLong())
            if (sim.pounds) {
                sim.convert(false)
                delay((CONVERT_TIME * 1000).toLong())
            }
            sim.glide(target.toFloat(), BACK_TIME)
            delay(((BACK_TIME + 0.6f) * 1000).toLong())
        }
        // Every turn is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        while (true) {
            for (move in Script) {
                val due = begin + ((turn * LOOP + move.at) * 1e9).toLong()
                delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                sim.glide((target + move.offset).toFloat(), move.time)
            }
            turn += 1
        }
    }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        // The canvas is larger than the card, so its shadow has room.
        Canvas(Modifier.fillMaxSize().bleed(BLEED.dp)) {
            // Reading the frame count here draws the canvas again after each step.
            if (frame < 0L) return@Canvas
            // Drawn in dp, with the card's top-left corner at the origin.
            scale(density, density, pivot = Offset.Zero) {
                translate(BLEED, BLEED) { drawCard(sim, title, message, measurer, cardShadow, bandShadow) }
            }
        }

        for (side in 0..1) {
            Box(
                Modifier
                    .offset((MIDDLE + if (side == 0) -SWITCH_W / 2f else 0f).dp, (SWITCH_Y - 22f).dp)
                    .size((SWITCH_W / 2f).dp, 44.dp)
                    .semantics { contentDescription = if (side == 0) "kg" else "lbs" }
                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                        touches += 1
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        sim.convert(side == 1)
                    },
            )
        }

        Box(
            Modifier
                .offset(0.dp, (DIAL_Y - 24f).dp)
                .size(WIDTH.dp, (HEIGHT - DIAL_Y + 24f).dp)
                .semantics {
                    contentDescription = title
                    stateDescription = "$reported ${if (pounds) "lbs" else "kg"}"
                }
                .pointerInput(sim) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        holding = true
                        touches += 1
                        sim.grab()
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) break
                            sim.drag((change.position.x - down.position.x) / density)
                            change.consume()
                        }
                        sim.release(tracker.calculateVelocity().x / density)
                        holding = false
                        touches += 1
                    }
                },
        )
    }
}

/** A line of text on [center], or with its left edge there. */
private fun DrawScope.write(measurer: TextMeasurer, text: String, style: TextStyle, center: Offset, alpha: Float = 1f, leading: Boolean = false) {
    val layout = measurer.measure(text, style, softWrap = false, maxLines = 1, density = UnitDensity)
    val left = if (leading) center.x else center.x - layout.size.width / 2f
    drawText(layout, topLeft = Offset(left, center.y - layout.size.height / 2f), alpha = alpha)
}

/** The point [radius] from the dial's centre, [degrees] clockwise from straight up. */
private fun at(degrees: Float, radius: Float): Offset {
    val angle = degrees * Math.PI.toFloat() / 180f
    return Offset(MIDDLE + sin(angle) * radius, CENTER_Y - cos(angle) * radius)
}

private fun DrawScope.drawCard(sim: Sim, title: String, message: String, measurer: TextMeasurer, cardShadow: SoftRoundRect, bandShadow: Paint) {
    val unit = if (sim.pounds) "lbs" else "kg"
    // 0 when the target is above today's weight, 1 when it is below.
    val loss = clamp01((sim.current - sim.value) / 0.6f + 0.5f)
    val tone = lerp(Leaf, Amber, loss)

    drawIntoCanvas { cardShadow.draw(it.nativeCanvas, 0f, 14f, WIDTH, HEIGHT, RADIUS) }
    val card = Path().apply { addRoundRect(RoundRect(0f, 0f, WIDTH, HEIGHT, CornerRadius(RADIUS))) }
    clipPath(card) {
        // A wash of lilac into aqua, fading to white at the top.
        drawRect(Brush.horizontalGradient(listOf(Color(0xFFD9C4F4), Color(0xFFBFEAF0)), startX = 0f, endX = WIDTH), size = Size(WIDTH, HEIGHT))
        drawRect(Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), startY = 20f, endY = 210f), size = Size(WIDTH, HEIGHT))

        write(measurer, title, TitleStyle, Offset(MIDDLE, TITLE_Y))
        val lines = message.split("\n")
        lines.forEachIndexed { n, line -> write(measurer, line, MessageStyle, Offset(MIDDLE, MESSAGE_Y + (n - (lines.size - 1) / 2f) * 15f)) }

        // The unit switch: a white capsule with a grey knob under the chosen unit.
        val half = SWITCH_W / 2f
        drawRoundRect(Color.White, Offset(MIDDLE - half, SWITCH_Y - SWITCH_H / 2f), Size(SWITCH_W, SWITCH_H), CornerRadius(SWITCH_H / 2f))
        drawRoundRect(
            Color(0xFFEEF0F2),
            Offset(MIDDLE - half + 3f + sim.knob * (half - 3f), SWITCH_Y - SWITCH_H / 2f + 3f),
            Size(half - 3f, SWITCH_H - 6f),
            CornerRadius((SWITCH_H - 6f) / 2f),
        )
        write(measurer, "kg", UnitStyle, Offset(MIDDLE - half / 2f + 1.5f, SWITCH_Y), alpha = mix(0.45f, 1f, clamp01(1f - sim.knob)))
        write(measurer, "lbs", UnitStyle, Offset(MIDDLE + half / 2f - 1.5f, SWITCH_Y), alpha = mix(0.45f, 1f, clamp01(sim.knob)))

        // The boxes: today's weight, a chevron, and the target, outlined, over the needle.
        drawBox(measurer, MIDDLE - BOX_GAP, sim.current, outlined = false, swell = 0f)
        drawBox(measurer, MIDDLE, sim.value, outlined = true, swell = sim.pulse)
        val bend = MIDDLE - BOX_GAP / 2f
        val chevron = Path().apply {
            moveTo(bend - 2.5f, BOX_Y - 5f)
            lineTo(bend + 2.5f, BOX_Y)
            lineTo(bend - 2.5f, BOX_Y + 5f)
        }
        drawPath(chevron, Steel, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // How far the target is from today's weight.
        val gap = sim.value.roundToInt() - sim.current.roundToInt()
        val sign = if (gap > 0) "+" else if (gap < 0) "−" else ""
        write(measurer, "$sign${abs(gap)} $unit", GapStyle.copy(color = tone), Offset(MIDDLE + BOX_W / 2f + 12f, BOX_Y), leading = true)

        val tip = TRIANGLE_Y - 5f + sim.bob
        val triangle = Path().apply {
            moveTo(MIDDLE, tip)
            lineTo(MIDDLE + 6.5f, tip + 10f)
            lineTo(MIDDLE - 6.5f, tip + 10f)
            close()
        }
        drawPath(triangle, Leaf)
        drawPath(triangle, Leaf, style = Stroke(2f, join = StrokeJoin.Round))

        // The dial: a white band with a paler disc inside it, both far larger than the card.
        val center = Offset(MIDDLE, CENTER_Y)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            drawIntoCanvas { it.nativeCanvas.drawCircle(MIDDLE, CENTER_Y - 2f, DIAL_R, bandShadow) }
        }
        drawCircle(Color.White, DIAL_R, center)
        drawCircle(Color(0xFFF1EEFA), DIAL_R - BAND, center)

        // The wash from today's weight to the needle, over the ticks.
        val start = ((sim.current - sim.value) * PITCH).coerceIn(-32f, 32f)
        if (abs(start) > 0.05f) {
            val steps = max(2, ceil(abs(start)).toInt())
            val wash = Path().apply {
                at(start, DIAL_R - 8f).let { moveTo(it.x, it.y) }
                for (n in 1..steps) at(start * (steps - n) / steps, DIAL_R - 8f).let { lineTo(it.x, it.y) }
                for (n in 0..steps) at(start * n / steps, DIAL_R - 46f).let { lineTo(it.x, it.y) }
                close()
            }
            drawPath(wash, tone, alpha = 0.25f)
        }

        // Ticks and numbers turn round the dial's centre and fade out towards the card's sides.
        val first = ceil(sim.value - 12f).toInt()
        for (mark in first..first + 24) {
            val degrees = (mark - sim.value) * PITCH
            val opacity = clamp01((27f - abs(degrees)) / 5f)
            if (opacity <= 0f) continue
            val long = mark % LONG_EVERY == 0
            drawLine(Steel, at(degrees, DIAL_R - 14f), at(degrees, DIAL_R - if (long) 40f else 30f), strokeWidth = 1.5f, cap = StrokeCap.Round, alpha = opacity)
            if (long) {
                val place = at(degrees, DIAL_R - 58f)
                rotate(degrees, pivot = place) { write(measurer, mark.toString(), MarkStyle, place, alpha = opacity) }
            }
        }

        drawLine(Leaf, at(sim.needle, DIAL_R), at(sim.needle, DIAL_R - 46f), strokeWidth = 2.5f, cap = StrokeCap.Round)
    }
}

/** One of the two boxes, centred on [x]. Its number rolls like a counter's wheel: most of the way through a unit it stands still. */
private fun DrawScope.drawBox(measurer: TextMeasurer, x: Float, weight: Float, outlined: Boolean, swell: Float) {
    scale(1f + swell, pivot = Offset(x, BOX_Y)) {
        val left = x - BOX_W / 2f
        val top = BOX_Y - BOX_H / 2f
        drawRoundRect(Color.White, Offset(left, top), Size(BOX_W, BOX_H), CornerRadius(11f))
        if (outlined) drawRoundRect(Leaf, Offset(left + 0.75f, top + 0.75f), Size(BOX_W - 1.5f, BOX_H - 1.5f), CornerRadius(10.25f), style = Stroke(1.5f))
        clipRect(left + 2f, top + 6f, left + BOX_W - 2f, top + BOX_H - 6f) {
            val below = floor(weight)
            val rolled = smooth(clamp01((weight - below - 0.2f) / 0.6f))
            for (step in 0..1) {
                write(measurer, (below.toInt() + step).toString(), FigureStyle, Offset(x, BOX_Y + (step - rolled) * 26f), alpha = if (step == 0) 1f - rolled else rolled)
            }
        }
    }
}
