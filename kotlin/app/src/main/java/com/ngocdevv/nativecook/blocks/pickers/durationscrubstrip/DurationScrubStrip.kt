package com.ngocdevv.nativecook.blocks.pickers.durationscrubstrip

import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val WIDTH = 330f
private const val HEIGHT = 53f

// Room above the strip for the pill, which straddles its top edge.
private const val TOP = 14f
private const val RADIUS = 20f
private const val PAD = 8f
private const val CHIP_W = 74f
private const val CHIP_RADIUS = 9f

// The ticks run from `TRACK_X` across `TRACK_W`.
private const val TRACK_X = 93f
private const val TRACK_W = 144f
private const val TICK_W = 1.5f
private const val TICK_H = 20f
private const val MARKER_W = 2f
private const val MARKER_H = 26f
private const val BADGE_H = 22f
private const val BADGE_PAD = 8f

// The comet's tail is this many ticks long.
private const val TAIL = 9f

// The pill holds up to this many characters ("11h 59m").
private const val SLOTS = 7

// The simulation's step, in seconds, and the longest a frame is counted as.
private const val TICK = 1f / 240f
private const val LONGEST_FRAME = 0.25f

private class Springy(val stiffness: Float, val damping: Float)

private val Follow = Springy(420f, 36f)
private val Ride = Springy(600f, 30f)
private val Stretch = Springy(400f, 30f)
private const val ROLL_TIME = 0.16f
private const val LIGHT_TIME = 0.03f
private const val DIM_TIME = 0.12f
private const val FLASH_TIME = 0.2f

// autoPlay (seconds). Each run: when it starts, the share of the range it goes to (null: back to
// the first duration), how long it takes.
private const val LOOP = 8f
private const val BACK_TIME = 0.7f

private class Run(val at: Float, val share: Float?, val time: Float)

private val Script = listOf(Run(0.8f, 0.325f, 1.1f), Run(2.5f, 0.686f, 1f), Run(4.1f, 1f, 0.8f), Run(5.8f, null, BACK_TIME))

// After a touch the loop waits this long before it starts over.
private const val RESUME_AFTER = 2f

private val Red = Color(0xFFFF3B1F)

// Text is measured at one pixel a dp and drawn on a canvas scaled to dp.
private val UnitDensity = Density(1f)
private val TimeStyle = TextStyle(
    color = Color.White,
    fontSize = 12.sp,
    fontWeight = FontWeight.Bold,
    fontFamily = FontFamily.Monospace,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
private val BadgeStyle = TextStyle(
    color = Color.White,
    fontSize = 11.sp,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Cubic ease-in-out, for the marker's runs. */
private fun inOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

/** "0m", "54m", "3h 54m", "12h". */
private fun labelOf(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    if (h == 0) return "${m}m"
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}

/** The room a character of the pill takes: digits all take the same, so the pill does not shiver. */
private fun advanceOf(character: Char) = when (character) {
    ' ' -> 3f
    'h' -> 6.6f
    'm' -> 9.8f
    else -> 6.8f
}

private fun widthOf(label: String) = BADGE_PAD * 2f + label.sumOf { advanceOf(it).toDouble() }.toFloat()

/** A time of day as "4:30 PM". */
private fun clock(minutes: Int): String {
    val day = (minutes % 1440 + 1440) % 1440
    val hour = day / 60 % 12
    val minute = day % 60
    return "${if (hour == 0) 12 else hour}:${if (minute < 10) "0" else ""}$minute ${if (day < 720) "AM" else "PM"}"
}

/** One character of the pill: the one it shows, the one it showed before, and how far the change has come, 0…1. */
private class Slot {
    var now: Char? = null
    var before: Char? = null
    var roll = 1f
}

/** Everything that moves, stepped in small fixed steps on every frame. */
private class Sim(val range: Float, val tickEvery: Float, start: Int) {
    /** Where the marker is heading and where it is, in minutes, and the whole minutes it reads as. */
    var target = start.toFloat()
    var value = target
    var minutes = start

    /** The pill's centre and width, in dp. */
    var badge = 0f
    var badgeWidth = 0f
    val slots = List(SLOTS) { Slot() }

    /** +1 when the duration last grew, -1 when it shrank: the way the characters roll. */
    var way = 1f

    /** Each tick: how lit it is, 0…1, and the flash it got when the marker passed. */
    val lit = FloatArray((range / tickEvery).toInt() + 1)
    val flash = FloatArray(lit.size)

    // The marker's journey when it travels by itself.
    private var from = 0f
    private var to = 0f
    private var elapsed = 0f
    private var duration = 0f
    private var valueSpeed = 0f
    private var badgeSpeed = 0f
    private var widthSpeed = 0f
    private var label = ""
    private val passed = BooleanArray(lit.size)

    init {
        settle()
    }

    /** A finger put the marker at [minutes]. */
    fun scrub(minutes: Float) {
        duration = 0f
        target = minutes
    }

    /** Sends the marker to [minutes] by itself, easing in and out. */
    fun glide(minutes: Float, time: Float) {
        from = target
        to = minutes
        elapsed = 0f
        duration = time
    }

    fun advance(gone: Float, still: Boolean) {
        if (gone <= 0f) return
        val steps = max(1, ceil(gone / TICK).toInt())
        repeat(steps) { move(gone / steps) }
        // Under reduced motion nothing glides: every change is a jump.
        if (still) settle()
    }

    private fun x(minutes: Float) = TRACK_X + minutes / range * TRACK_W

    private fun move(h: Float) {
        if (duration > 0f) {
            elapsed += h
            val t = min(1f, elapsed / duration)
            target = from + (to - from) * inOut(t)
            if (t >= 1f) duration = 0f
        }

        // The marker follows its target on a firm spring, the pill follows the marker on a loose one.
        valueSpeed += (Follow.stiffness * (target - value) - Follow.damping * valueSpeed) * h
        value += valueSpeed * h
        badgeSpeed += (Ride.stiffness * (x(value) - badge) - Ride.damping * badgeSpeed) * h
        badge += badgeSpeed * h

        read()
        widthSpeed += (Stretch.stiffness * (widthOf(label) - badgeWidth) - Stretch.damping * widthSpeed) * h
        badgeWidth += widthSpeed * h
        for (slot in slots) slot.roll = min(1f, slot.roll + h / ROLL_TIME)

        val at = value / tickEvery
        for (i in lit.indices) {
            val on = i <= at + 0.02f
            if (on && !passed[i]) flash[i] = 1f
            passed[i] = on
            // A tick lights at once and goes out slowly.
            lit[i] += ((if (on) 1f else 0f) - lit[i]) * (1f - exp(-h / (if (on) LIGHT_TIME else DIM_TIME)))
            flash[i] *= exp(-h / FLASH_TIME)
        }
    }

    /** Turns the marker's place into whole minutes and gives each character of the pill its place. */
    private fun read() {
        val whole = value.coerceIn(0f, range).roundToInt()
        if (whole == minutes && label.isNotEmpty()) return
        way = if (whole >= minutes) 1f else -1f
        minutes = whole
        label = labelOf(whole)
        slots.forEachIndexed { i, slot ->
            val character = label.getOrNull(i)
            if (character != slot.now) {
                slot.before = slot.now
                slot.now = character
                slot.roll = 0f
            }
        }
    }

    private fun settle() {
        value = target
        valueSpeed = 0f
        badge = x(target)
        badgeSpeed = 0f
        label = ""
        read()
        badgeWidth = widthOf(label)
        widthSpeed = 0f
        for (slot in slots) slot.roll = 1f
        for (i in lit.indices) {
            passed[i] = i <= target / tickEvery + 0.02f
            lit[i] = if (passed[i]) 1f else 0f
            flash[i] = 0f
        }
    }
}

/**
 * A compact duration scrubber. A dark strip holds the start time and the end time in two chips and
 * a row of ticks between them; a red marker stands in the ticks at the chosen duration, with the
 * duration in a pill riding above it. Dragging along the strip (or tapping it) moves the marker:
 * the ticks it passes light up behind it like a comet's tail, the pill's digits roll, and the end
 * time follows.
 *
 * @param range The longest duration, in minutes.
 * @param tickEvery Minutes between two ticks.
 * @param minutes The duration at first; [autoPlay] comes back to it at the end of every loop.
 * @param start When the stretch of time begins, in minutes after midnight.
 * @param autoPlay Scrubs to three durations and back by itself, in a loop.
 * @param onChange Called with the duration in minutes whenever it changes.
 */
@Composable
fun DurationScrubStrip(
    range: Int = 720,
    tickEvery: Int = 30,
    minutes: Int = 0,
    start: Int = 16 * 60 + 30,
    autoPlay: Boolean = false,
    onChange: ((minutes: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val span = max(range, 1)
    val every = max(tickEvery, 1)
    val auto = autoPlay && !still && range > 0
    val rest = minutes.coerceIn(0, span)
    val view = LocalView.current
    val changed by rememberUpdatedState(onChange)
    val measurer = rememberTextMeasurer(cacheSize = 48)

    val sim = remember(span, every, rest) { Sim(span.toFloat(), every.toFloat(), rest) }
    var reported by remember(sim) { mutableIntStateOf(rest) }
    // Counts frames, so the canvas is drawn again after each step.
    var frame by remember { mutableLongStateOf(0L) }
    // Counts touches: each one stops the loop and restarts the wait before it resumes.
    var touches by remember { mutableIntStateOf(0) }
    // A finger is on the strip.
    var holding by remember { mutableStateOf(false) }

    LaunchedEffect(sim, still) {
        var before = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            sim.advance(min((now - before) / 1e9f, LONGEST_FRAME), still)
            before = now
            if (sim.minutes != reported) {
                reported = sim.minutes
                changed?.invoke(sim.minutes)
            }
            frame += 1
        }
    }

    // The script a finger would follow: three runs up the strip, each further, and a sweep back.
    LaunchedEffect(auto, touches, holding, sim) {
        if (!auto || holding) return@LaunchedEffect
        if (touches > 0) {
            // A touch took over: wait, go back to the first duration and start over.
            delay((RESUME_AFTER * 1000).toLong())
            sim.glide(rest.toFloat(), BACK_TIME)
            delay((BACK_TIME * 1000).toLong())
        }
        // Every run is timed from the script's start, so the loop keeps its period.
        val begin = System.nanoTime()
        var turn = 0
        while (true) {
            for (run in Script) {
                val due = begin + ((turn * LOOP + run.at) * 1e9).toLong()
                delay(((due - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                sim.glide(run.share?.let { (it * span).roundToInt().toFloat() } ?: rest.toFloat(), run.time)
            }
            turn += 1
        }
    }

    Canvas(
        modifier
            .size(WIDTH.dp, (TOP + HEIGHT).dp)
            .semantics {
                contentDescription = "Duration"
                stateDescription = labelOf(reported)
            }
            .pointerInput(sim) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    holding = true
                    touches += 1
                    // The tick the finger last passed, so each one is felt once.
                    var notch = (sim.target / every).toInt()
                    fun scrub(x: Float) {
                        val target = (clamp01((x / density - TRACK_X) / TRACK_W) * span).roundToInt()
                        if (target / every != notch) {
                            notch = target / every
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        sim.scrub(target.toFloat())
                    }
                    scrub(down.position.x)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        scrub(change.position.x)
                        change.consume()
                    }
                    holding = false
                    touches += 1
                }
            },
    ) {
        // Reading the frame count here draws the canvas again after each step.
        if (frame < 0L) return@Canvas
        // Drawn in dp, with the strip's top edge `TOP` down.
        scale(density, density, pivot = Offset.Zero) { drawStrip(sim, start, measurer) }
    }
}

/** A line of text centred on [center]. */
private fun DrawScope.write(measurer: TextMeasurer, text: String, style: TextStyle, center: Offset, alpha: Float = 1f) {
    val layout = measurer.measure(text, style, softWrap = false, maxLines = 1, density = UnitDensity)
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f), alpha = alpha)
}

private fun DrawScope.drawStrip(sim: Sim, start: Int, measurer: TextMeasurer) {
    val middle = TOP + HEIGHT / 2f

    drawRoundRect(Color(0xFF1B1B1B), Offset(0f, TOP), Size(WIDTH, HEIGHT), CornerRadius(RADIUS))
    drawRoundRect(Color(0xFF3A3A3A), Offset(0.5f, TOP + 0.5f), Size(WIDTH - 1f, HEIGHT - 1f), CornerRadius(RADIUS - 0.5f), style = Stroke(1f))

    val chipH = HEIGHT - PAD * 2f
    for ((x, minutes) in listOf(PAD to start, WIDTH - PAD - CHIP_W to start + sim.minutes)) {
        drawRoundRect(Color(0xFF2A2A2A), Offset(x, TOP + PAD), Size(CHIP_W, chipH), CornerRadius(CHIP_RADIUS))
        drawRoundRect(Color(0xFF3F3F3F), Offset(x + 0.5f, TOP + PAD + 0.5f), Size(CHIP_W - 1f, chipH - 1f), CornerRadius(CHIP_RADIUS - 0.5f), style = Stroke(1f))
        write(measurer, clock(minutes), TimeStyle, Offset(x + CHIP_W / 2f, middle))
    }

    val last = max(sim.lit.size - 1, 1)
    val at = sim.value / sim.range * last
    for (i in sim.lit.indices) {
        val x = TRACK_X + i.toFloat() / last * TRACK_W
        // Not reached: dim, and dimmer towards the end. Passed: bright just behind the marker, greyer further back.
        val dim = mix(0.16f, 0.06f, i.toFloat() / last)
        val bright = mix(0.92f, 0.34f, clamp01((at - i) / TAIL))
        val glow = mix(dim, bright, sim.lit[i])
        drawRoundRect(
            Color.White,
            Offset(x - TICK_W / 2f, middle - TICK_H / 2f),
            Size(TICK_W, TICK_H),
            CornerRadius(TICK_W / 2f),
            alpha = clamp01(glow + (1f - glow) * sim.flash[i] * sim.lit[i]),
        )
    }

    val x = TRACK_X + sim.value / sim.range * TRACK_W
    drawRoundRect(Red, Offset(x - MARKER_W / 2f, middle - MARKER_H / 2f), Size(MARKER_W, MARKER_H), CornerRadius(MARKER_W / 2f))

    // The pill leans back while it is behind the marker, about the middle of its lower edge.
    rotate(((sim.badge - x) * 0.6f).coerceIn(-8f, 8f), pivot = Offset(sim.badge, TOP + BADGE_H / 2f)) {
        drawRoundRect(Red, Offset(sim.badge - sim.badgeWidth / 2f, TOP - BADGE_H / 2f), Size(sim.badgeWidth, BADGE_H), CornerRadius(BADGE_H / 2f))

        // The characters are laid out by what the pill reads now; one that changed rolls in over the one it replaces.
        var left = sim.badge - sim.slots.sumOf { slot -> slot.now?.let { advanceOf(it).toDouble() } ?: 0.0 }.toFloat() / 2f
        for (slot in sim.slots) {
            val room = advanceOf(slot.now ?: slot.before ?: ' ')
            val center = left + room / 2f
            val eased = 1f - (1f - slot.roll).pow(3)
            slot.before?.let { if (eased < 1f) write(measurer, it.toString(), BadgeStyle, Offset(center, TOP - sim.way * 9f * eased), alpha = 1f - eased) }
            slot.now?.let {
                write(measurer, it.toString(), BadgeStyle, Offset(center, TOP + sim.way * 9f * (1f - eased)), alpha = eased)
                left += room
            }
        }
    }
}
