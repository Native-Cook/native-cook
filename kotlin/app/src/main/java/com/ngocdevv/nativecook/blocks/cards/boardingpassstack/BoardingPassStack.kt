package com.ngocdevv.nativecook.blocks.cards.boardingpassstack

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.drop

/**
 * @param color The pass's colour at its top.
 * @param deep The pass's colour at its bottom.
 * @param arrival Arrival time at the destination, shown in the header.
 * @param localTime Local time at the destination, shown in the header.
 */
data class BoardingPass(
    val color: Color,
    val deep: Color,
    val fromCity: String,
    val fromCode: String,
    val toCity: String,
    val toCode: String,
    val arrival: String,
    val localTime: String,
    val flight: String,
    val date: String,
    val boarding: String,
    val cabin: String,
    val gate: String,
    val seat: String,
    val status: String,
    val sequence: String,
    val bags: String,
)

private const val WIDTH = 340f
private const val HEIGHT = 600f
private const val RADIUS = 32f
private const val GLOW_RADIUS = 190f

// Header: the city's box, the row of three items, and the boxes its values roll through.
private const val CITY_BOX = 54f
private const val ROW_Y = 112f
private const val ROW_HEIGHT = 44f
private const val COLUMN = 100f
private const val TIME_BOX = 28f

private const val PASS_X = 20f
private const val PASS_WIDTH = 300f
private const val PASS_HEIGHT = 330f
private const val PASS_RADIUS = 12f

// Height of the strip with the airline, the gate and the seat.
private const val STRIP = 52f

// Where the stub tears off, and the radius of the notch at each end of that line.
private const val TEAR = 230f
private const val NOTCH = 7f

// Top of the front pass in the panel, and of the area that takes the stack's touches.
private const val FRONT_Y = 246f
private const val STACK_Y = 170f

/** Where a pass rests in the stack: its top edge above the front's, scale, darkening and opacity. */
private class Slot(val top: Float, val scale: Float, val shade: Float, val alpha: Float)

private val SlotFront = Slot(0f, 1f, 0f, 1f)
private val SlotA = Slot(-34f, 0.94f, 0.16f, 1f)
private val SlotB = Slot(-68f, 0.88f, 0.3f, 1f)

// Passes deeper than the second stay hidden under it.
private val SlotHidden = Slot(SlotB.top, SlotB.scale, SlotB.shade, 0f)

// How far the front pass ducks down, and how far the next one is pulled up, before they swap.
private const val SINK = 190f
private const val LIFT = 130f

// Degrees the pulled pass tips its top edge towards the viewer, and the eye's distance in dp.
private const val TILT = 14f
private const val PERSPECTIVE = 900f

// Dp the plane flies in from the left.
private const val FLIGHT = 56f

// Seconds one shuffle takes, and the share of it after which the two passes have swapped depth.
private const val SHUFFLE = 1.3f
private const val SHUFFLE_REDUCED = 0.35f
private const val SWAP = 0.45f

// The spring the pulled pass lands on.
private const val STIFFNESS = 170f
private const val DAMPING = 16f
private const val MASS = 1f

// autoPlay (seconds): the three default passes go round once per LOOP, the first pull at FIRST
// and the next ones every STEP.
private const val LOOP = 7.2f
private const val FIRST = 0.8f
private const val STEP = LOOP / 3f

// After a touch the loop waits this long before it starts again.
private const val USER_HOLD = 2.5f

// Finger travel upwards that pulls the next pass, and the most a tap may move.
private const val SWIPE = 36f
private const val TAP_SLOP = 8f

// A pass's layer is this much larger than the pass on every side, so its shadow is not cut.
private const val PASS_BLEED = 44f

private val Ink = Color(0xFF1B2233)

private val Passes = listOf(
    BoardingPass(
        color = Color(0xFFF0BD2E), deep = Color(0xFFDBA616),
        fromCity = "Berlin", fromCode = "BER", toCity = "Lisbon", toCode = "LIS",
        arrival = "19:30", localTime = "18:30", flight = "TN582", date = "03 OCT", boarding = "17:35",
        cabin = "First", gate = "Z52", seat = "2A", status = "Gold", sequence = "042", bags = "2 × 32 kg",
    ),
    BoardingPass(
        color = Color(0xFFF58468), deep = Color(0xFFE5674A),
        fromCity = "Oslo", fromCode = "OSL", toCity = "Tokyo", toCode = "HND",
        arrival = "08:05", localTime = "16:05", flight = "TN204", date = "11 NOV", boarding = "12:10",
        cabin = "Business", gate = "C14", seat = "7K", status = "Gold", sequence = "118", bags = "2 × 23 kg",
    ),
    BoardingPass(
        color = Color(0xFF86DCBB), deep = Color(0xFF62C79F),
        fromCity = "Madrid", fromCode = "MAD", toCity = "Reykjavik", toCode = "KEF",
        arrival = "23:45", localTime = "21:45", flight = "TN931", date = "24 JAN", boarding = "20:15",
        cabin = "Economy", gate = "A7", seat = "23F", status = "Gold", sequence = "207", bags = "1 × 23 kg",
    ),
)

// A plane seen from above, flying to the right, in a 24 × 24 box, as x, y pairs.
private val PlaneOutline = floatArrayOf(
    22f, 12f, 20.6f, 12.75f, 15f, 12.9f, 10.2f, 20.5f, 8.2f, 20.5f, 10.6f, 12.9f, 5.6f, 12.85f, 4.2f, 16.2f, 2.8f, 16.2f,
    3.4f, 12f, 2.8f, 7.8f, 4.2f, 7.8f, 5.6f, 11.15f, 10.6f, 11.1f, 8.2f, 3.5f, 10.2f, 3.5f, 15f, 11.1f, 20.6f, 11.25f,
)

/** The outline of a pass: a rounded rectangle with a half-round notch at each end of the tear line. */
private fun passOutline() = Path().apply {
    val w = PASS_WIDTH
    val h = PASS_HEIGHT
    val r = PASS_RADIUS
    val n = NOTCH
    moveTo(r, 0f)
    lineTo(w - r, 0f)
    arcTo(Rect(w - 2f * r, 0f, w, 2f * r), -90f, 90f, false)
    lineTo(w, TEAR - n)
    arcTo(Rect(w - n, TEAR - n, w + n, TEAR + n), -90f, -180f, false)
    lineTo(w, h - r)
    arcTo(Rect(w - 2f * r, h - 2f * r, w, h), 0f, 90f, false)
    lineTo(r, h)
    arcTo(Rect(0f, h - 2f * r, 2f * r, h), 90f, 90f, false)
    lineTo(0f, TEAR + n)
    arcTo(Rect(-n, TEAR - n, n, TEAR + n), 90f, -180f, false)
    lineTo(0f, r)
    arcTo(Rect(0f, 0f, 2f * r, 2f * r), 180f, 90f, false)
    close()
}

/** Whether square (x, y) of the code is dark. The pattern only has to look right: it encodes nothing. */
private fun codeSquare(x: Int, y: Int, seed: Int): Boolean {
    // The corner marks: a 7 × 7 ring around a 3 × 3 block, with a clear band around them.
    for ((cx, cy) in listOf(0 to 0, 14 to 0, 0 to 14)) {
        val lx = x - cx
        val ly = y - cy
        if (lx in -1..7 && ly in -1..7) {
            if (lx < 0 || lx > 6 || ly < 0 || ly > 6) return false
            return lx == 0 || lx == 6 || ly == 0 || ly == 6 || (lx in 2..4 && ly in 2..4)
        }
    }
    return (x * 73 + y * 151 + x * y * 17 + (seed + 1) * 31) % 97 % 2 == 0
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun passText(
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    color: Color,
    spacing: Float = 0f,
    align: TextAlign = TextAlign.Start,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    textAlign = align,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val CityNameStyle = passText(42f, CITY_BOX, FontWeight.ExtraLight, Color.White, align = TextAlign.Center)
private val DestinationStyle = passText(10f, 14f, FontWeight.SemiBold, rgba(255, 255, 255, 0.45f), 1.5f, TextAlign.Center)
private val TimeStyle = passText(22f, TIME_BOX, FontWeight.ExtraLight, Color.White, align = TextAlign.Center)
private val TimeLabelStyle = passText(10f, 13f, FontWeight.Normal, rgba(255, 255, 255, 0.5f), align = TextAlign.Center)
private val RideStyle = passText(15f, 20f, FontWeight.Light, Color.White, align = TextAlign.Center)
private val AirlineStyle = passText(15f, 20f, FontWeight.Bold, Ink)
private val FieldLabelStyle = passText(7.5f, 10f, FontWeight.Bold, Ink.copy(alpha = 0.55f), 0.3f)
private val FieldValueStyle = passText(13f, 17f, FontWeight.Normal, Ink)
private val FieldLargeStyle = passText(16f, 20f, FontWeight.Normal, Ink)
private val CityStyle = passText(9f, 12f, FontWeight.SemiBold, Ink.copy(alpha = 0.62f), 0.4f)
private val CodeStyle = passText(44f, 48f, FontWeight.ExtraLight, Ink.copy(alpha = 0.92f))
private val RouteStyle = passText(12f, 16f, FontWeight.SemiBold, Ink.copy(alpha = 0.62f))

private class Pose {
    /** Top edge, measured from where the front pass rests. */
    var top = 0f
    var scale = 1f

    /** Degrees around the horizontal axis; negative tips the top edge towards the viewer. */
    var tilt = 0f

    /** Opacity of the dark veil on a pass behind. */
    var shade = 0f
    var alpha = 1f

    /** How far the plane has flown, 0…1. */
    var flight = 1f
    var z = 0f
}

/** Per-frame state of the stack. [pos] counts the shuffles so far; its fraction is how far the running one has come. */
private class Deck {
    val pos = mutableFloatStateOf(0f)
    var target = 0f

    /** True while the loop is the one moving the stack. */
    var live = false

    /** Shuffles asked for by a finger that the next frame has to start. */
    var requests = 0

    /** Where the loop started counting from, and its clock. */
    var base = 0f
    var clock = 0f

    /** Seconds the loop still waits after a touch. */
    var hold = 0f
}

private fun wrapIndex(index: Int, count: Int) = ((index % count) + count) % count

private fun segment(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun easeOut(t: Float) = 1f - (1f - t).pow(3)

private fun easeInOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (2f - 2f * t).pow(3) / 2f

private fun smooth(t: Float) = t * t * (3f - 2f * t)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Step response of the landing spring [seconds] after it is released: 0 → 1 with one overshoot. */
private fun spring(seconds: Float): Float {
    if (seconds <= 0f) return 0f
    val omega = sqrt(STIFFNESS / MASS)
    val zeta = DAMPING / (2f * sqrt(STIFFNESS * MASS))
    val damped = omega * sqrt(1f - zeta * zeta)
    return 1f - exp(-zeta * omega * seconds) * (cos(damped * seconds) + zeta * omega / damped * sin(damped * seconds))
}

/** Shuffles done [clock] seconds into the autoPlay loop. */
private fun script(clock: Float, count: Int): Float {
    var done = 0f
    for (index in 0 until count) {
        done += segment(clock, FIRST + STEP * index, FIRST + STEP * index + SHUFFLE)
    }
    return done
}

/** Place of pass [index] in the stack: 0 in front, 1 and 2 behind it, and below 0 while it ducks away. */
private fun relative(index: Int, pos: Float, count: Int): Float {
    var place = (index - pos) % count
    if (place <= -1f) place += count
    if (place > count - 1f) place -= count
    return place
}

/** The same place counted from the nearest side, for things that cross-fade: −0.5 … 0.5 around the front. */
private fun centered(index: Int, pos: Float, count: Int): Float {
    var place = (index - pos) % count
    if (place <= -count / 2f) place += count
    if (place > count / 2f) place -= count
    return place
}

/** The header changes later than the stack: its position moves only during a part of each shuffle. */
private fun headerPosition(pos: Float, from: Float, to: Float): Float {
    val whole = floor(pos)
    return whole + easeInOut(segment(pos - whole, from, to))
}

/** Opacity of a value that rolls through its box at [place]: it fades only as it leaves the box. */
private fun rollFade(place: Float) = smooth(((0.75f - abs(place)) / 0.3f).coerceIn(0f, 1f))

/** Where a pass rests [depth] places behind the front. */
private fun slot(depth: Int) = when (depth) {
    0 -> SlotFront
    1 -> SlotA
    2 -> SlotB
    else -> SlotHidden
}

private fun pose(at: Float, count: Int, reduced: Boolean): Pose {
    // Reduced motion: every pass jumps to its next place half-way while the stack fades through.
    var place = at
    var fade = 1f
    if (reduced) {
        val nearest = Math.round(place).toFloat()
        fade = 1f - 2f * abs(place - nearest)
        place = nearest
    }

    val p = Pose()
    if (place <= 0f) {
        // The front pass ducks down, and once the next one is over it, comes up again at the back.
        val t = -place
        val back = slot(count - 1)
        if (t < SWAP) {
            val down = easeInOut(segment(t, 0f, 0.42f))
            p.top = SINK * down
            p.scale = 1f - 0.03f * down
            p.shade = 0.1f * down
            p.z = 3f
        } else {
            val up = easeOut(segment(t, SWAP, 0.95f))
            p.top = mix(SINK, back.top, up)
            p.scale = mix(0.97f, back.scale, up)
            p.shade = mix(0.1f, back.shade, up)
            p.alpha = mix(1f, back.alpha, up)
        }
    } else if (place <= 1f) {
        // The next pass is pulled up out of the stack, tips towards the viewer and drops in front.
        val part = 1f - place
        val from = slot(1)
        val rise = easeInOut(segment(part, 0f, 0.42f))
        if (part < SWAP) {
            p.top = from.top - LIFT * rise
            p.scale = mix(from.scale, 1.03f, rise)
            p.z = 2f
        } else {
            val landed = mix(spring((part - SWAP) * SHUFFLE), 1f, smooth(segment(part, 0.88f, 1f)))
            p.top = mix(from.top - LIFT, 0f, landed)
            p.scale = mix(1.03f, 1f, landed)
            p.z = 4f
        }
        p.tilt = -TILT * sin(PI.toFloat() * segment(part, 0.08f, 0.85f))
        p.shade = from.shade * (1f - rise)
        p.flight = easeOut(segment(part, 0.6f, 1f))
    } else {
        // The passes further back move up one place.
        val depth = ceil(place).toInt()
        val from = slot(depth)
        val to = slot(depth - 1)
        val step = easeInOut(segment(depth - place, 0.3f, 0.8f))
        p.top = mix(from.top, to.top, step)
        p.scale = mix(from.scale, to.scale, step)
        p.shade = mix(from.shade, to.shade, step)
        p.alpha = mix(from.alpha, to.alpha, step)
        p.z = if (depth == 2) 1f else 0f
    }
    p.alpha *= fade
    return p
}

/**
 * A wallet of boarding passes on a dark panel, under a header with the destination of the pass in
 * front. A tap on a pass behind, or a swipe up, shuffles the stack: the front pass ducks down, the
 * next one is pulled up out of the stack, tips towards the viewer and drops in front, and the old
 * one tucks in at the back. The header rolls to the new city and times, and the small plane
 * between the airport codes flies across once.
 *
 * @param passes The passes, front first. At least three.
 * @param autoPlay Brings the next pass to the front every 2.4 seconds, as if a finger pulled it.
 * @param onChange Called with the index of the pass that is now in front.
 */
@Composable
fun BoardingPassStack(
    passes: List<BoardingPass> = Passes,
    autoPlay: Boolean = false,
    airline: String = "Tern Airways",
    passenger: String = "Lena Hartwell",
    destinationLabel: String = "DESTINATION",
    arrivalLabel: String = "Arrival time",
    localTimeLabel: String = "Local time",
    rideLabel: String = "Book a ride",
    onChange: ((index: Int) -> Unit)? = null,
    onRide: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val count = passes.size
    val duration = if (reducedMotion) SHUFFLE_REDUCED else SHUFFLE
    val view = LocalView.current
    val changed by rememberUpdatedState(onChange)

    val deck = remember { Deck() }

    LaunchedEffect(auto) {
        deck.hold = 0f
        deck.clock = 0f
        deck.base = Math.round(deck.pos.floatValue).toFloat()
    }

    LaunchedEffect(count, duration) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            var p = deck.pos.floatValue
            if (looping && deck.hold <= 0f) {
                val loop = STEP * count
                deck.clock += dt
                if (deck.clock >= loop) deck.clock -= loop
                p = deck.base + script(deck.clock, count)
                deck.target = p
                deck.live = true
            } else {
                if (deck.live) {
                    // A touch took over: the shuffle the loop was in the middle of finishes first.
                    deck.target = ceil(p - 0.000001f)
                    deck.live = false
                }
                if (deck.requests > 0) {
                    deck.target = min(deck.target + deck.requests, floor(p) + 2f)
                    deck.requests = 0
                }
                if (p < deck.target) {
                    p = min(deck.target, p + dt / duration)
                } else if (deck.hold > 0f) {
                    deck.hold -= dt
                    if (deck.hold <= 0f) {
                        p = wrapIndex(p.roundToInt(), count).toFloat()
                        deck.target = p
                        deck.base = p
                        deck.clock = 0f
                    }
                }
            }
            deck.pos.floatValue = p
        }
    }

    val front by remember(count) { derivedStateOf { wrapIndex(deck.pos.floatValue.roundToInt(), count) } }
    LaunchedEffect(count) {
        snapshotFlow { wrapIndex(deck.pos.floatValue.roundToInt(), count) }.drop(1).collect { changed?.invoke(it) }
    }

    // A touch takes over from the loop, which starts again from rest a little later.
    fun pull(by: Int) {
        deck.hold = USER_HOLD
        deck.requests += by
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .clip(RoundedCornerShape(RADIUS.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF161D30), Color(0xFF0A0E18)))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            surface(0.dp) { _, _, _, _ ->
                fun glow(color: Color, opacity: Float, x: Float, y: Float) = drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = opacity), color.copy(alpha = 0f)), Offset(x, y), GLOW_RADIUS),
                    GLOW_RADIUS,
                    Offset(x, y),
                )
                glow(Color(0xFF4A6FE0), 0.2f, 60f, 30f)
                // A second light in the colour of the pass in front.
                for ((index, pass) in passes.withIndex()) {
                    val near = max(0f, 1f - abs(centered(index, deck.pos.floatValue, count)))
                    if (near > 0f) glow(pass.color, 0.2f * near, 290f, 120f)
                }
                // Dashed rules between the three items of the header.
                for (x in listOf(20f + COLUMN, 20f + COLUMN * 2f)) {
                    drawLine(
                        rgba(255, 255, 255, 0.22f),
                        Offset(x, ROW_Y + 3f),
                        Offset(x, ROW_Y + ROW_HEIGHT - 3f),
                        1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
                    )
                }
            }
        }

        Rolling(
            texts = passes.map { it.toCity },
            style = CityNameStyle,
            box = CITY_BOX,
            reduced = reducedMotion,
            position = { headerPosition(deck.pos.floatValue, 0.3f, 0.75f) },
            modifier = Modifier.offset(20.dp, 26.dp).width((WIDTH - 40f).dp).semantics { heading() },
        )
        BasicText(destinationLabel, Modifier.offset(0.dp, 82.dp).width(WIDTH.dp), style = DestinationStyle, maxLines = 1)

        for (side in 0..1) {
            Column(Modifier.offset((20f + side * COLUMN * 2f).dp, ROW_Y.dp).width(COLUMN.dp).semantics(mergeDescendants = true) {}) {
                Rolling(
                    texts = passes.map { if (side == 0) it.arrival else it.localTime },
                    style = TimeStyle,
                    box = TIME_BOX,
                    reduced = reducedMotion,
                    position = { headerPosition(deck.pos.floatValue, 0.55f, 0.98f) },
                    modifier = Modifier.width(COLUMN.dp),
                )
                Spacer(Modifier.height(2.dp))
                BasicText(if (side == 0) arrivalLabel else localTimeLabel, Modifier.width(COLUMN.dp), style = TimeLabelStyle, maxLines = 1)
            }
        }
        Box(
            Modifier
                .offset((20f + COLUMN).dp, ROW_Y.dp)
                .size(COLUMN.dp, ROW_HEIGHT.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = { onRide?.invoke() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(rideLabel, style = RideStyle, maxLines = 1)
        }

        passes.forEachIndexed { index, pass ->
            PassCard(
                pass = pass,
                airline = airline,
                passenger = passenger,
                seed = index,
                isFront = index == front,
                current = { pose(relative(index, deck.pos.floatValue, count), count, reducedMotion) },
            )
        }

        // Touches on the stack: a tap on a strip behind, or a swipe up anywhere on it.
        Box(
            Modifier
                .zIndex(9f)
                .offset(0.dp, STACK_Y.dp)
                .size(WIDTH.dp, (HEIGHT - STACK_Y).dp)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Next pass") {
                            pull(1)
                            true
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        val dx = (up.position.x - down.position.x) / density
                        val dy = (up.position.y - down.position.y) / density
                        if (dy < -SWIPE && abs(dy) > abs(dx)) {
                            pull(1)
                        } else if (abs(dx) < TAP_SLOP && abs(dy) < TAP_SLOP) {
                            // A tap: the strips of the two passes behind lie above the front pass.
                            val y = down.position.y / density
                            val resting = abs(deck.pos.floatValue - Math.round(deck.pos.floatValue)) < 0.001f
                            if (y < FRONT_Y - STACK_Y && resting) pull(if (y < FRONT_Y + SlotB.top - STACK_Y) 2 else 1)
                        }
                    }
                },
        )
    }
}

/** A value of the header in the box it rolls through: the old one leaves upwards as the new one comes up from below. */
@Composable
private fun Rolling(texts: List<String>, style: TextStyle, box: Float, reduced: Boolean, position: () -> Float, modifier: Modifier = Modifier) {
    Box(modifier.height(box.dp).clipToBounds()) {
        texts.forEachIndexed { index, text ->
            BasicText(
                text,
                Modifier.matchParentSize().graphicsLayer {
                    val place = centered(index, position(), texts.size)
                    translationY = if (reduced) 0f else (place * box).dp.toPx()
                    alpha = if (reduced) max(0f, 1f - abs(place)) else rollFade(place)
                },
                style = style,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** One boarding pass: strip, route, fields, and the stub with its code below the tear line. */
@Composable
private fun PassCard(pass: BoardingPass, airline: String, passenger: String, seed: Int, isFront: Boolean, current: () -> Pose) {
    val rank by remember(current) { derivedStateOf { current().z } }
    val outline = remember { passOutline() }
    val shadow = remember { SoftRoundRect(rgba(0, 0, 0, 0.3f), sigma = 14f) }
    val description = "$airline, ${pass.fromCity} to ${pass.toCity}, flight ${pass.flight}, gate ${pass.gate}, seat ${pass.seat}"

    Box(
        Modifier
            .zIndex(rank)
            .offset((PASS_X - PASS_BLEED).dp, (FRONT_Y - PASS_BLEED).dp)
            // The layer is wider than the panel, so it must not be squeezed into it.
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .size((PASS_WIDTH + PASS_BLEED * 2f).dp, (PASS_HEIGHT + PASS_BLEED * 2f).dp)
            .graphicsLayer {
                val p = current()
                translationY = (p.top - (1f - p.scale) * PASS_HEIGHT / 2f).dp.toPx()
                scaleX = p.scale
                scaleY = p.scale
                // Android turns the other way around this axis, and measures the eye's distance
                // in 72nds of an inch of raw pixels.
                rotationX = -p.tilt
                cameraDistance = PERSPECTIVE.dp.toPx() / 72f
                alpha = p.alpha
            }
            .clearAndSetSemantics { if (isFront) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            surface(PASS_BLEED.dp) { x, y, w, h -> drawIntoCanvas { shadow.draw(it.nativeCanvas, x, y - 4f, w, h, PASS_RADIUS) } }
        }
        Box(Modifier.size(PASS_WIDTH.dp, PASS_HEIGHT.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(0.dp) { _, _, _, _ ->
                    drawPath(outline, Brush.verticalGradient(listOf(pass.color, pass.deep), startY = 0f, endY = PASS_HEIGHT))
                    // The strip is a shade lighter than the body.
                    clipPath(outline) { drawRect(rgba(255, 255, 255, 0.1f), size = Size(PASS_WIDTH, STRIP)) }
                    drawLine(Ink.copy(alpha = 0.14f), Offset(0f, STRIP + 0.5f), Offset(PASS_WIDTH, STRIP + 0.5f), 1f)
                    // The perforation between the two notches.
                    drawLine(
                        Ink.copy(alpha = 0.3f),
                        Offset(NOTCH + 6f, TEAR),
                        Offset(PASS_WIDTH - NOTCH - 6f, TEAR),
                        1.2f,
                        StrokeCap.Round,
                        PathEffect.dashPathEffect(floatArrayOf(1f, 5f)),
                    )
                    // The airline's mark: a tern's wing in a ring, in a 20 × 20 box.
                    val mark = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val markTop = (STRIP - 20f) / 2f
                    drawCircle(Ink, 8.5f, Offset(18f + 10f, markTop + 10f), style = mark)
                    drawPath(
                        Path().apply {
                            moveTo(18f + 5f, markTop + 12.6f)
                            lineTo(18f + 8.8f, markTop + 8.4f)
                            lineTo(18f + 10.8f, markTop + 11f)
                            lineTo(18f + 15f, markTop + 6.6f)
                        },
                        Ink,
                        style = mark,
                    )
                    // A drawn stand-in for the pass's code: 21 × 21 squares with the three corner marks of a QR code.
                    drawRoundRect(rgba(255, 255, 255, 0.92f), Offset(18f, 244f), Size(72f, 72f), CornerRadius(8f))
                    val unit = 58f / 21f
                    for (cy in 0 until 21) {
                        for (cx in 0 until 21) {
                            if (codeSquare(cx, cy, seed)) drawRect(Ink, Offset(25f + cx * unit, 251f + cy * unit), Size(unit, unit))
                        }
                    }
                }
            }

            BasicText(airline, Modifier.offset(46.dp, 0.dp).height(STRIP.dp).wrapContentHeight(), style = AirlineStyle, maxLines = 1)
            Row(
                Modifier.offset(18.dp, 0.dp).size((PASS_WIDTH - 36f).dp, STRIP.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Field("GATE", pass.gate, large = true, end = true)
                Field("SEAT", pass.seat, large = true, end = true)
            }

            Row(Modifier.offset(18.dp, 66.dp).width((PASS_WIDTH - 36f).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(pass.fromCity.uppercase(), style = CityStyle, maxLines = 1)
                BasicText(pass.toCity.uppercase(), style = CityStyle, maxLines = 1)
            }
            Row(Modifier.offset(16.dp, 78.dp).width((PASS_WIDTH - 32f).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(pass.fromCode, style = CodeStyle, maxLines = 1)
                BasicText(pass.toCode, style = CodeStyle, maxLines = 1)
            }
            // The plane flies in from the origin's side each time the pass arrives in front.
            Canvas(
                Modifier
                    .offset((PASS_WIDTH / 2f - 13f).dp, 89.dp)
                    .size(26.dp)
                    .graphicsLayer {
                        val flight = current().flight
                        val grown = 0.8f + 0.2f * flight
                        translationX = (-FLIGHT * (1f - flight)).dp.toPx()
                        scaleX = grown
                        scaleY = grown
                        alpha = segment(flight, 0f, 0.3f)
                    },
            ) {
                val unit = size.width / 24f
                val plane = Path()
                for (index in PlaneOutline.indices step 2) {
                    if (index == 0) plane.moveTo(PlaneOutline[0] * unit, PlaneOutline[1] * unit) else plane.lineTo(PlaneOutline[index] * unit, PlaneOutline[index + 1] * unit)
                }
                plane.close()
                drawPath(plane, Ink.copy(alpha = 0.7f))
            }

            Row(Modifier.offset(18.dp, 140.dp).width((PASS_WIDTH - 36f).dp)) {
                Field("FLIGHT", pass.flight, Modifier.width(74.dp))
                Field("DATE", pass.date, Modifier.width(72.dp))
                Field("BOARDING", pass.boarding, Modifier.width(62.dp))
                Spacer(Modifier.weight(1f))
                Field("CLASS", pass.cabin, end = true)
            }
            Row(Modifier.offset(18.dp, 184.dp).width((PASS_WIDTH - 36f).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Field("PASSENGER", passenger)
                Field("STATUS", pass.status, end = true)
            }

            Column(Modifier.offset(108.dp, 247.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Field("SEQUENCE", pass.sequence)
                Field("BAGS", pass.bags)
            }
            BasicText(
                "${pass.fromCode} – ${pass.toCode}",
                Modifier.align(Alignment.TopEnd).offset((-18).dp, 247.dp),
                style = RouteStyle,
                maxLines = 1,
            )

            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = current().shade }) {
                scale(density, density, pivot = Offset.Zero) { drawPath(outline, Color.Black) }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, large: Boolean = false, end: Boolean = false) {
    Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        BasicText(label, style = FieldLabelStyle, maxLines = 1, softWrap = false)
        Spacer(Modifier.height(1.dp))
        BasicText(value, style = if (large) FieldLargeStyle else FieldValueStyle, maxLines = 1, softWrap = false)
    }
}
