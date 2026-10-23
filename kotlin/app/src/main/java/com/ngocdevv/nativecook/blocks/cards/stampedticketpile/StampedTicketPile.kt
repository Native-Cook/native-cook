package com.ngocdevv.nativecook.blocks.cards.stampedticketpile

import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class StampKind {
    /** Two rings; the first line runs along the top of the inner one. */
    Ring,
    Plate,
    Oval,
    Triangle,
}

/**
 * A passport stamp: an outline with three short lines of text in it.
 *
 * @param lines A small line, the date in large type, and another small line.
 * @param x Centre of the stamp on its ticket.
 * @param angle Its turn in degrees.
 */
data class TicketStamp(val kind: StampKind, val lines: List<String>, val ink: Color, val x: Float, val y: Float, val angle: Float)

/** @param title One label strip per line. */
data class TripTicket(val color: Color, val date: String, val title: List<String>, val place: String, val stamp: TicketStamp)

private const val WIDTH = 350f
private const val HEIGHT = 460f

private const val TICKET_WIDTH = 196f
private const val TICKET_HEIGHT = 280f
private const val TICKET_RADIUS = 18f

// Radius of the half circle cut out of the bottom edge.
private const val NOTCH = 20f

// Where the tickets lie, the top of the pile first: offset from the middle, and turn in degrees.
private val SLOTS = arrayOf(
    floatArrayOf(34f, 66f, -9f),
    floatArrayOf(-44f, 24f, 8f),
    floatArrayOf(40f, -34f, 16f),
    floatArrayOf(-40f, -60f, -15f),
)

// Two shadows cross-faded: a tight one on the pile, a wide one while the ticket is lifted.
private const val SHADOW_OPACITY = 0.16f
private const val SHADOW_RADIUS = 9f
private const val SHADOW_Y = 6f
private const val LIFT_SHADOW_OPACITY = 0.2f
private const val LIFT_SHADOW_RADIUS = 18f
private const val LIFT_SHADOW_Y = 16f

// Each ticket's canvas overflows it by this much on every side to fit the shadows.
private val BLEED = 60.dp

private const val LEFT = 20f
private const val CHIP_TOP = 24f
private const val CHIP_HEIGHT = 18f
private const val CHIP_PAD = 7f
private const val CHIP_RADIUS = 6f
private const val TITLE_TOP = 50f
private const val STRIP_LEFT = 16f
private const val STRIP_HEIGHT = 30f
private const val STRIP_STEP = 27f
private const val STRIP_PAD = 9f
private const val STRIP_RADIUS = 7f
private const val STRIP_TILT = -2f
private const val PLACE_GAP = 12f

private const val STAMP_BOX = 100f
private const val STAMP_STROKE = 2.2f
private const val STAMP_HAIRLINE = 0.9f
private const val STAMP_DASH = 2.2f

// The ring's first line: the radius it runs along, and the angle from one letter to the next.
private const val ARC_RADIUS = 28.5f
private const val ARC_STEP = 14f
private val TRIANGLE = floatArrayOf(50f, 6f, 96f, 86f, 4f, 86f)
private val TRIANGLE_INNER = floatArrayOf(50f, 20f, 84f, 79f, 16f, 79f)

/** Where a kind of stamp puts its three lines, and how large it sets them. */
private class StampLook(
    val topY: Float,
    val topSize: Float,
    val topTracking: Float,
    val mainY: Float,
    val mainSize: Float,
    val bottomY: Float,
    val bottomSize: Float,
    val bottomTracking: Float,
)

private val STAMP_LOOKS = mapOf(
    StampKind.Ring to StampLook(0f, 8f, 0f, 52f, 14f, 66f, 7.5f, 1f),
    StampKind.Plate to StampLook(33f, 7f, 1.2f, 49.5f, 11.5f, 66f, 6.5f, 0.8f),
    StampKind.Oval to StampLook(36f, 6.5f, 1.2f, 50f, 12.5f, 64f, 6.5f, 0.8f),
    StampKind.Triangle to StampLook(47f, 7f, 0.8f, 61f, 11.5f, 73f, 6f, 0.6f),
)

// Tickets settle on a spring with a small overshoot.
private const val MASS = 1f
private const val STIFFNESS = 210f
private const val DAMPING = 19f

// A shuffle: the top ticket goes out to the side for OUT_TIME, then springs back in underneath.
private const val OUT_TIME = 0.34f
private const val SIDE_X = 330f
private const val SIDE_Y = -26f
private const val SIDE_TURN = 16f
private const val LIFT_SCALE = 0.05f

// The other tickets wait this long before they move up.
private const val SHIFT_DELAY = 0.1f

// Under reduced motion the shuffled ticket fades in at the back instead.
private const val FADE_TIME = 0.25f

// The drop: one ticket after another falls out of the bottom, speeding up.
private const val DROP_STAGGER = 0.07f
private const val DROP_TIME = 0.5f
private const val DROP_Y = 760f
private const val DROP_DRIFT = 30f
private const val DROP_TURN = 24f

// The deal: a ticket every DEAL_GAP flies in from below, and is stamped STAMP_AFTER its start.
private const val DEAL_GAP = 0.65f
private const val DEAL_Y = 640f
private const val DEAL_DRIFT = 46f
private const val DEAL_TURN = 28f
private const val DEAL_SCALE = 0.08f
private const val STAMP_AFTER = 0.44f

// Seconds a whole deal takes, the last stamp included.
private const val DEAL_TIME = 2.9f

// The stamp comes down from STAMP_SCALE in SLAM seconds while its ink appears, and shakes the ticket.
private const val SLAM = 0.14f
private const val STAMP_SCALE = 1.6f
private const val INK_IN = 0.07f
private const val INK_FRESH = 0.96f
private const val INK_DRY = 0.8f
private const val INK_DRY_RATE = 8f
private const val SHAKE = 1.8f
private const val SHAKE_DECAY = 16f
private const val SHAKE_RATE = 46f
private const val DIP = 0.02f
private const val DIP_DECAY = 20f

// autoPlay loop (seconds): four shuffles bring the pile back to its order, it drops, and it is dealt again.
private const val FIRST_AT = 0.8f
private const val SHUFFLE_GAP = 0.8f
private const val DROP_AT = 4.2f
private const val DEAL_AT = 5f
private const val LOOP = 8.6f

// After a tap the loop waits this long once the shuffle is over.
private const val USER_HOLD = 2f

// Without autoPlay the pile is dealt this long after it appears.
private const val ENTRANCE_AT = 0.2f

private val SampleTickets = listOf(
    TripTicket(
        color = Color(0xFFFFB877), date = "Jul 11 – Sep 6", title = listOf("Surfing and", "beaches"), place = "Denpasar, Kuta and Umalas",
        stamp = TicketStamp(StampKind.Triangle, listOf("ENTRY", "11 JUL", "GATE 7"), Color(0xFF1D1B1A), x = 118f, y = 196f, angle = 14f),
    ),
    TripTicket(
        color = Color(0xFFEAE1DA), date = "Jan 7 – Jan 16", title = listOf("Ancient Egypt", "adventure"), place = "Cairo and Alexandria",
        stamp = TicketStamp(StampKind.Ring, listOf("ARRIVAL", "07 JAN", "2025"), Color(0xFF34389A), x = 120f, y = 198f, angle = -14f),
    ),
    TripTicket(
        color = Color(0xFFDFE4FF), date = "Apr 21 – Jun 7", title = listOf("Road trip", "with friends"), place = "Lisbon, Porto and Faro",
        stamp = TicketStamp(StampKind.Plate, listOf("ENTRY PERMIT", "21 APR 2025", "VISITOR · 90 DAYS"), Color(0xFFA5362C), x = 112f, y = 200f, angle = -9f),
    ),
    TripTicket(
        color = Color(0xFFFFE500), date = "Mar 28 – Apr 4", title = listOf("Spring in", "the Alps"), place = "Chamonix and Annecy",
        stamp = TicketStamp(StampKind.Oval, listOf("DEPARTED", "28 MAR", "TERMINAL 2"), Color(0xFF1D1B1A), x = 116f, y = 198f, angle = 11f),
    ),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun ticketText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    textAlign = TextAlign.Center,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

// Control-point distance that makes a cubic curve a quarter circle.
private const val K = 0.5523f

/** A rounded rectangle with a half circle cut out of the middle of its bottom edge. */
private val TicketOutline = Path().apply {
    val w = TICKET_WIDTH
    val h = TICKET_HEIGHT
    val r = TICKET_RADIUS
    val n = NOTCH
    moveTo(r, 0f)
    lineTo(w - r, 0f)
    cubicTo(w - r + r * K, 0f, w, r - r * K, w, r)
    lineTo(w, h - r)
    cubicTo(w, h - r + r * K, w - r + r * K, h, w - r, h)
    lineTo(w / 2f + n, h)
    cubicTo(w / 2f + n, h - n * K, w / 2f + n * K, h - n, w / 2f, h - n)
    cubicTo(w / 2f - n * K, h - n, w / 2f - n, h - n * K, w / 2f - n, h)
    lineTo(r, h)
    cubicTo(r - r * K, h, 0f, h - r + r * K, 0f, h - r)
    lineTo(0f, r)
    cubicTo(0f, r - r * K, r - r * K, 0f, r, 0f)
    close()
}

/** A map pin, 8 × 11, with its tip at the bottom. */
private val Pin = Path().apply {
    moveTo(4f, 0f)
    cubicTo(1.8f, 0f, 0f, 1.8f, 0f, 4f)
    cubicTo(0f, 7f, 4f, 11f, 4f, 11f)
    cubicTo(4f, 11f, 8f, 7f, 8f, 4f)
    cubicTo(8f, 1.8f, 6.2f, 0f, 4f, 0f)
    close()
}

private fun triangle(points: FloatArray) = Path().apply {
    moveTo(points[0], points[1])
    lineTo(points[2], points[3])
    lineTo(points[4], points[5])
    close()
}

/** The ticket's outline filled with a soft edge, for its shadow. Drawn in the current (dp) units. */
private class TicketShadow(opacity: Float, sigma: Float) {
    private val outline = TicketOutline.asAndroidPath()
    private val baseAlpha = opacity
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.Black.toArgb()
        // Hardware canvases only honour mask filters from API 28.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }

    fun draw(canvas: android.graphics.Canvas, alpha: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        paint.alpha = (baseAlpha * alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawPath(outline, paint)
    }
}

private enum class Kind { Rest, Settle, Tuck, Drop, Deal, Fade }

/** Where a ticket is: its offset from the middle of the pile, its turn in degrees, and its place in the stack. */
private class Pose(
    var x: Float,
    var y: Float,
    var turn: Float,
    var scale: Float = 1f,
    var opacity: Float = 1f,
    /** 0…1: how far the ticket is off the pile, which softens its shadow. */
    var lift: Float = 0f,
    var z: Float,
) {
    fun mixed(other: Pose, p: Float, z: Float) = Pose(
        x = x + (other.x - x) * p, y = y + (other.y - y) * p, turn = turn + (other.turn - turn) * p,
        scale = scale + (other.scale - scale) * p, z = z,
    )

    fun copy() = Pose(x, y, turn, scale, opacity, lift, z)
}

/** A ticket's place at rest: slot 0 is the top of the pile. */
private fun slotPose(slot: Int, count: Int) = Pose(SLOTS[slot][0], SLOTS[slot][1], SLOTS[slot][2], z = (count - slot).toFloat())

private fun smooth(t: Float): Float {
    val p = t.coerceIn(0f, 1f)
    return p * p * (3f - 2f * p)
}

/** A spring let go at 0 and settling at 1, [t] seconds on, with a small overshoot. */
private fun spring(t: Float): Float {
    val decay = DAMPING / (2f * MASS)
    val omega = sqrt(STIFFNESS / MASS - decay * decay)
    return 1f - exp(-decay * t) * (cos(omega * t) + decay / omega * sin(omega * t))
}

/** Scale of the stamp [since] seconds after it starts coming down: it shrinks onto the paper. */
private fun stampScale(since: Float) = when {
    since <= 0f -> STAMP_SCALE
    since < SLAM -> STAMP_SCALE - (STAMP_SCALE - 1f) * (since / SLAM) * (since / SLAM)
    else -> 1f
}

/** Opacity of the stamp: its ink appears as it comes down, then dries a little. */
private fun stampOpacity(since: Float) = when {
    since <= 0f -> 0f
    since < SLAM -> INK_FRESH * (since / INK_IN).coerceIn(0f, 1f)
    else -> INK_DRY + (INK_FRESH - INK_DRY) * exp(-INK_DRY_RATE * (since - SLAM))
}

/** Where every ticket is, and where it is going. */
private class PileSim(val count: Int) {
    var clock = 0f

    /** The next step of the loop's script. */
    var step = 0

    /** Seconds the loop still waits after a touch. */
    var hold = 0f

    /** Taps are ignored until then: the pile is dropping or being dealt. */
    var busyUntil = 0f

    /** Shuffles so far: every other one leaves by the other side. */
    var shuffles = 0

    // One entry per ticket.
    val slot = IntArray(count) { it }
    val kind = Array(count) { Kind.Rest }
    val start = FloatArray(count)
    val from = Array(count) { slotPose(it, count) }
    val side = FloatArray(count) { 1f }

    /** When each ticket's stamp starts coming down. */
    val stampAt = FloatArray(count) { -100f }

    // Written last in a frame: reading it is what redraws the pile.
    val time = mutableFloatStateOf(0f)

    fun reset() {
        clock = 0f
        step = 0
        hold = 0f
        busyUntil = 0f
        shuffles = 0
        for (index in 0 until count) {
            slot[index] = index
            kind[index] = Kind.Rest
            stampAt[index] = -100f
        }
        front.intValue = 0
    }

    /** The ticket on top of the pile. */
    val front = mutableIntStateOf(0)

    fun advance(dt: Float, looping: Boolean) {
        val now = time.floatValue + dt
        if (hold > 0f) {
            hold -= dt
            // Time is up: the loop starts over from its rest, with the pile as it is.
            if (hold <= 0f) {
                clock = 0f
                step = 0
            }
        } else if (looping) {
            clock += dt
            if (clock >= LOOP) {
                clock -= LOOP
                step = 0
            }
            // The script: one shuffle per ticket, then the pile drops and is dealt again.
            while (step < count + 2) {
                val at = if (step < count) FIRST_AT + step * SHUFFLE_GAP else if (step == count) DROP_AT else DEAL_AT
                if (clock < at) break
                val then = now - (clock - at)
                when {
                    step < count -> shuffle(then, reduced = false)
                    step == count -> drop(then)
                    else -> deal(then)
                }
                step += 1
            }
        }
        time.floatValue = now
    }

    /** The top ticket leaves by the side and comes back under the pile; the others move up a place. */
    fun shuffle(now: Float, reduced: Boolean) {
        for (index in 0 until count) {
            from[index] = poseAt(index, now)
            if (slot[index] == 0) {
                slot[index] = count - 1
                kind[index] = if (reduced) Kind.Fade else Kind.Tuck
                start[index] = now
                side[index] = if (shuffles % 2 == 0) 1f else -1f
            } else {
                slot[index] -= 1
                kind[index] = if (reduced) Kind.Rest else Kind.Settle
                start[index] = now + SHIFT_DELAY
            }
        }
        shuffles += 1
        front.intValue = slot.indexOf(0)
    }

    /** The tickets fall out of the bottom, the top one first. */
    fun drop(now: Float) {
        for (index in 0 until count) {
            from[index] = poseAt(index, now)
            kind[index] = Kind.Drop
            start[index] = now + slot[index] * DROP_STAGGER
            side[index] = if (slot[index] % 2 == 0) 1f else -1f
        }
        busyUntil = now + DEAL_AT - DROP_AT + DEAL_TIME
    }

    /** The tickets fly in from below, the bottom one first, and each is stamped as it lands. */
    fun deal(now: Float) {
        for (index in 0 until count) {
            kind[index] = Kind.Deal
            start[index] = now + (count - 1 - slot[index]) * DEAL_GAP
            side[index] = if (slot[index] % 2 == 0) 1f else -1f
            stampAt[index] = start[index] + STAMP_AFTER
        }
        busyUntil = max(busyUntil, now + DEAL_TIME)
    }

    /** Where a ticket is, shaken by the blow of its stamp. */
    fun pose(index: Int): Pose {
        val now = time.floatValue
        val pose = poseAt(index, now)
        val since = now - stampAt[index] - SLAM
        if (since > 0f) {
            pose.turn += SHAKE * exp(-SHAKE_DECAY * since) * sin(SHAKE_RATE * since)
            pose.scale *= 1f - DIP * exp(-DIP_DECAY * since)
        }
        return pose
    }

    private fun poseAt(index: Int, now: Float): Pose {
        val home = slotPose(slot[index], count)
        val t = now - start[index]
        return when (kind[index]) {
            Kind.Rest -> home
            Kind.Settle -> from[index].mixed(home, if (t > 0f) spring(t) else 0f, home.z)
            Kind.Tuck -> {
                // Out past the edge of the pile, lifted and turning, then in again underneath.
                val out = from[index].copy()
                out.x += side[index] * SIDE_X
                out.y += SIDE_Y
                out.turn += side[index] * SIDE_TURN
                out.scale += LIFT_SCALE
                if (t < OUT_TIME) {
                    val p = smooth(t / OUT_TIME)
                    from[index].mixed(out, p, (count + 1).toFloat()).also { it.lift = sin(PI.toFloat() * p) }
                } else {
                    out.mixed(home, spring(t - OUT_TIME), home.z)
                }
            }
            Kind.Drop -> {
                val p = (t / DROP_TIME).coerceIn(0f, 1f)
                from[index].copy().also {
                    it.x += side[index] * DROP_DRIFT * p * p
                    it.y += DROP_Y * p * p
                    it.turn += side[index] * DROP_TURN * p * p
                    it.opacity = 1f - smooth((p - 0.6f) / 0.4f)
                }
            }
            Kind.Deal -> {
                val below = home.copy()
                below.x += side[index] * DEAL_DRIFT
                below.y += DEAL_Y
                below.turn += side[index] * DEAL_TURN
                below.scale += DEAL_SCALE
                below.opacity = 0f
                if (t <= 0f) {
                    below
                } else {
                    val p = spring(t)
                    below.mixed(home, p, home.z).also { it.lift = (1f - p).coerceIn(0f, 1f) }
                }
            }
            Kind.Fade -> home.also { it.opacity = smooth(t / FADE_TIME) }
        }
    }
}

/**
 * A loose pile of four trip tickets, each a different colour and angle, with the trip's dates,
 * its name on black label strips, a place and a passport stamp. The tickets are dealt from below
 * one by one and stamped as they land; a tap sends the top one out to the side and under the
 * pile while the others move up.
 *
 * @param tickets Four tickets, the top of the pile first.
 * @param autoPlay Shuffles through the pile, drops it, deals it again and starts over, every 8.6 s.
 * @param onShuffle Called with the index of the ticket a tap brings to the top.
 */
@Composable
fun StampedTicketPile(
    tickets: List<TripTicket> = SampleTickets,
    autoPlay: Boolean = false,
    labelColor: Color = Color(0xFF0E0E10),
    labelTextColor: Color = Color.White,
    inkColor: Color = Color(0xFF1C1C1C),
    onShuffle: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val looping by rememberUpdatedState(auto)
    val count = min(tickets.size, SLOTS.size)
    val view = LocalView.current
    val sim = remember(count) { PileSim(count) }
    val shadow = remember { TicketShadow(SHADOW_OPACITY, SHADOW_RADIUS) }
    val liftShadow = remember { TicketShadow(LIFT_SHADOW_OPACITY, LIFT_SHADOW_RADIUS) }

    LaunchedEffect(sim, auto, reducedMotion) {
        sim.reset()
        // Without the loop the pile makes its entrance once: dealt and stamped.
        if (!auto && !reducedMotion) sim.deal(sim.time.floatValue + ENTRANCE_AT)
    }

    LaunchedEffect(sim) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            if (dt > 0f) sim.advance(dt, looping)
        }
    }

    Box(
        modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                // A tap on the pile: the top ticket goes to the back, unless the pile is being dealt.
                val now = sim.time.floatValue
                if (count > 1 && now >= sim.busyUntil) {
                    sim.shuffle(now, reducedMotion)
                    sim.hold = SHUFFLE_GAP + USER_HOLD
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onShuffle?.invoke(sim.front.intValue)
                }
            }
            .semantics { contentDescription = tickets.getOrNull(sim.front.intValue)?.title?.joinToString(" ") ?: "" },
        contentAlignment = Alignment.Center,
    ) {
        for (index in 0 until count) {
            val ticket = tickets[index]
            val z by remember(sim, index) { derivedStateOf { sim.pose(index).z } }
            Box(
                Modifier
                    .zIndex(z)
                    .size(TICKET_WIDTH.dp, TICKET_HEIGHT.dp)
                    .graphicsLayer {
                        val pose = sim.pose(index)
                        translationX = pose.x.dp.toPx()
                        translationY = pose.y.dp.toPx()
                        rotationZ = pose.turn
                        scaleX = pose.scale
                        scaleY = pose.scale
                        alpha = pose.opacity
                        // No offscreen buffer for a fading ticket: it would cut the shadow off at the ticket's edge.
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .clearAndSetSemantics {},
            ) {
                TicketFace(ticket, index, sim, shadow, liftShadow, labelColor, labelTextColor, inkColor)
            }
        }
    }
}

/** One ticket: dates, the trip's name on label strips, the place and the stamp. */
@Composable
private fun TicketFace(
    ticket: TripTicket,
    index: Int,
    sim: PileSim,
    shadow: TicketShadow,
    liftShadow: TicketShadow,
    labelColor: Color,
    labelTextColor: Color,
    inkColor: Color,
) {
    Box(Modifier.fillMaxSize().bleed(BLEED)) {
        Canvas(Modifier.fillMaxSize()) {
            val lift = sim.pose(index).lift
            surface(BLEED) { x, y, _, _ ->
                drawIntoCanvas {
                    val canvas = it.nativeCanvas
                    canvas.save()
                    canvas.translate(x, y + SHADOW_Y)
                    shadow.draw(canvas, 1f - lift)
                    canvas.translate(0f, LIFT_SHADOW_Y - SHADOW_Y)
                    liftShadow.draw(canvas, lift)
                    canvas.restore()
                }
                translate(x, y) { drawPath(TicketOutline, ticket.color) }
            }
        }
    }

    BasicText(
        ticket.date,
        style = ticketText(9.5f, 13f, FontWeight.Medium, inkColor),
        maxLines = 1,
        modifier = Modifier
            .offset(LEFT.dp, CHIP_TOP.dp)
            .height(CHIP_HEIGHT.dp)
            .background(rgba(0, 0, 0, 0.07f), RoundedCornerShape(CHIP_RADIUS.dp))
            .padding(horizontal = CHIP_PAD.dp)
            .wrapContentHeight(),
    )

    ticket.title.forEachIndexed { line, text ->
        BasicText(
            text,
            style = ticketText(19f, 24f, FontWeight.Bold, labelTextColor, -0.2f),
            maxLines = 1,
            modifier = Modifier
                .offset(STRIP_LEFT.dp, (TITLE_TOP + line * STRIP_STEP).dp)
                .height(STRIP_HEIGHT.dp)
                .graphicsLayer {
                    // The strips are stuck on by hand, each a little askew.
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationZ = if (line % 2 == 0) STRIP_TILT else -STRIP_TILT * 0.75f
                }
                .background(labelColor, RoundedCornerShape(STRIP_RADIUS.dp))
                .padding(horizontal = STRIP_PAD.dp)
                .wrapContentHeight(),
        )
    }

    Row(
        Modifier.offset(LEFT.dp, (TITLE_TOP + ticket.title.size * STRIP_STEP + PLACE_GAP).dp).height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(8.dp, 11.dp)) {
            scale(density, density, pivot = Offset.Zero) {
                drawPath(Pin, inkColor)
                drawCircle(ticket.color, 1.5f, Offset(4f, 4f))
            }
        }
        BasicText(ticket.place, style = ticketText(9.5f, 12f, FontWeight.Normal, inkColor), maxLines = 1)
    }

    Box(
        Modifier
            .offset((ticket.stamp.x - STAMP_BOX / 2f).dp, (ticket.stamp.y - STAMP_BOX / 2f).dp)
            .size(STAMP_BOX.dp)
            .graphicsLayer {
                val since = sim.time.floatValue - sim.stampAt[index]
                val size = stampScale(since)
                rotationZ = ticket.stamp.angle
                scaleX = size
                scaleY = size
                alpha = stampOpacity(since)
            },
    ) {
        StampMark(ticket.stamp)
    }
}

/** A stamp in a 100-dp box: its outline, and its three lines of text. */
@Composable
private fun StampMark(stamp: TicketStamp) {
    val look = STAMP_LOOKS.getValue(stamp.kind)
    val outer = remember { triangle(TRIANGLE) }
    val inner = remember { triangle(TRIANGLE_INNER) }

    Canvas(Modifier.fillMaxSize()) {
        scale(density, density, pivot = Offset.Zero) {
            val bold = Stroke(STAMP_STROKE, join = StrokeJoin.Round)
            val fine = Stroke(STAMP_HAIRLINE, join = StrokeJoin.Round)
            val dashed = Stroke(STAMP_HAIRLINE, pathEffect = PathEffect.dashPathEffect(floatArrayOf(STAMP_DASH, STAMP_DASH)))
            val middle = Offset(STAMP_BOX / 2f, STAMP_BOX / 2f)
            when (stamp.kind) {
                StampKind.Ring -> {
                    drawCircle(stamp.ink, 42f, middle, style = bold)
                    drawCircle(stamp.ink, 36f, middle, style = dashed)
                }
                StampKind.Plate -> {
                    drawRoundRect(stamp.ink, Offset(2f, 20f), Size(96f, 60f), CornerRadius(8f), style = bold)
                    drawRoundRect(stamp.ink, Offset(7f, 25f), Size(86f, 50f), CornerRadius(4f), style = fine)
                }
                StampKind.Oval -> {
                    drawOval(stamp.ink, Offset(2f, 19f), Size(96f, 62f), style = bold)
                    drawOval(stamp.ink, Offset(7.5f, 24.5f), Size(85f, 51f), style = dashed)
                }
                StampKind.Triangle -> {
                    drawPath(outer, stamp.ink, style = bold)
                    drawPath(inner, stamp.ink, style = fine)
                }
            }
        }
    }

    if (stamp.kind == StampKind.Ring) {
        // The first line follows the inner ring, one letter at a time.
        val letters = stamp.lines[0]
        letters.forEachIndexed { index, letter ->
            val angle = (index - (letters.length - 1) / 2f) * ARC_STEP
            val radians = angle * PI.toFloat() / 180f
            BasicText(
                letter.toString(),
                style = ticketText(look.topSize, 16f, FontWeight.ExtraBold, stamp.ink),
                modifier = Modifier
                    .offset((STAMP_BOX / 2f + ARC_RADIUS * sin(radians) - 8f).dp, (STAMP_BOX / 2f - ARC_RADIUS * cos(radians) - 8f).dp)
                    .size(16.dp)
                    .graphicsLayer { rotationZ = angle },
            )
        }
    } else {
        StampLine(stamp.lines[0], look.topY, look.topSize, FontWeight.ExtraBold, look.topTracking, stamp.ink)
    }
    StampLine(stamp.lines[1], look.mainY, look.mainSize, FontWeight.ExtraBold, 0.4f, stamp.ink)
    StampLine(stamp.lines[2], look.bottomY, look.bottomSize, FontWeight.Bold, look.bottomTracking, stamp.ink)
}

/** A line of a stamp, centred on [y]. */
@Composable
private fun StampLine(text: String, y: Float, size: Float, weight: FontWeight, tracking: Float, ink: Color) {
    BasicText(
        text,
        style = ticketText(size, size * 2f, weight, ink, tracking),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.offset(0.dp, (y - size).dp).width(STAMP_BOX.dp).wrapContentWidth(unbounded = true),
    )
}
