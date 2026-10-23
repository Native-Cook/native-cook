package com.ngocdevv.nativecook.blocks.onboarding.watchfuleyespermission

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// A permission primer told by a pair of eyes. The screen has four pages; a change from one to the
// next is a Stage, and every layer is a function of the stage and of the seconds since it began.
// What a touch changes (the page, the switches) is stored; nothing is stored per frame. Lengths are in dp.

internal const val CHAT = 0
internal const val ASK = 1
internal const val SWITCHES = 2
internal const val DONE = 3

/** The change from one page to another that began at [t0], on the screen's clock. */
internal class Stage(val from: Int, val to: Int, val t0: Double)

// What a tap can mean. A switch is TAP_SWITCH plus its row.
internal const val TAP_NONE = -1
internal const val TAP_CHAT = 0
internal const val TAP_START = 1
internal const val TAP_LATER = 2
internal const val TAP_ALL = 3
internal const val TAP_PILL = 4
internal const val TAP_SWITCH = 5

/** autoPlay is back at the chat every LOOP seconds. In between it taps these, at these seconds. */
internal const val LOOP = 10.0

internal class Tap(val at: Double, val tap: Int)

internal val Play = listOf(Tap(1.5, TAP_CHAT), Tap(4.4, TAP_START), Tap(6.3, TAP_PILL), Tap(9.2, TAP_PILL))

/** A loop starts with the chat this long on the screen. */
internal const val REST = 0.8

/** After a touch autoPlay waits this long before it goes back to the chat and starts over. */
internal const val USER_HOLD = 3.0

// A page takes taps once it has been arriving this long: its eyes are in place by then.
private val Ready = doubleArrayOf(0.75, 1.8, 1.3, 0.6)

// Sizes are in dp of a 393 × 852 screen. The eyes hang from the safe area's top, the middle of
// every page from the screen's middle, and the buttons stand on the safe area's floor.
internal const val EYES_TOP = 37.0
internal const val EYES_HOVER = -176.0
internal const val EYES_ASK = -86.0
internal const val CHAT_TOP = 89.0
internal const val BUBBLE_SIDE = 32.0
internal const val BUBBLE_BOTTOM = 118.0
internal const val ROW_SIDE = 40.0
internal const val ROW_BOTTOM = 58.0
internal const val ROW_HEIGHT = 48.0
internal const val LATER_BOTTOM = 18.0
internal const val LATER_REACH = 56.0
internal const val LATER_HEIGHT = 36.0
internal const val PILL_SIDE = 32.0
internal const val PILL_BOTTOM = 10.0
internal const val PILL_HEIGHT = 46.0
internal const val PHONE_HALF = 136.0
internal const val PHONE_TOP = -171.0
internal const val PHONE_RADIUS = 44.0
private const val PHONE_NOTCH = 46.0
private const val PHONE_DEPTH = 16.0
private const val PHONE_FILLET = 8.0
internal const val TILE_Y = -66.0
internal const val TILE_SIZE = 54.0
internal const val TILE_RADIUS = 13.0
internal const val TILE_HEART = 26.0
internal const val TITLE_Y = -16.0
internal const val ALL_TOP = 12.0
internal const val ALL_HEIGHT = 30.0
internal const val LIST_WIDTH = 226.0
internal const val LIST_TOP = 74.0
internal const val LIST_PITCH = 23.5
internal const val LIST_BAR = 152.0
internal const val LIST_THICK = 9.0
internal const val TOGGLE_WIDTH = 26.0
internal const val TOGGLE_HEIGHT = 15.0
internal const val TOGGLE_KNOB = 11.0

/** The check mark of the last page; the switches shrink into its middle. */
internal const val MARK_Y = 44.0
internal const val MARK_DISC = 80.0
internal const val LABEL_Y = 126.0

/** Switches in the list. */
internal const val COUNT = 6

// The order the switches' page comes in: the tile, its name, the first row, the switches, the button.
internal const val ITEM_TILE = 0
internal const val ITEM_TITLE = 1
internal const val ITEM_ALL = 2
internal const val ITEM_ROW = 3
private const val ITEM_PILL = 9

// The eyes: small at the top, large in the home.
private const val SMALL_SIZE = 14.0
private const val SMALL_GAP = 34.0
private const val SMALL_GLOW = 22.0
private const val LARGE_SIZE = 26.0
private const val LARGE_GAP = 72.0
private const val LARGE_GLOW = 48.0

/** The soft light round the large eyes reaches this far. */
internal const val HALO = 130.0
private val Travel = Spring(stiffness = 120.0, damping = 17.5)

// Down to the hover first, then a slower drift on into the home.
private const val DRIFT_AT = 0.6
private const val DRIFT_FOR = 0.6

// A blink shuts the eyes to this share of their height. Per page: the first one, and how often after.
private const val SHUT = 0.14
private const val BLINK = 0.18
private val BlinkFirst = doubleArrayOf(1.25, 2.3, 3.6, 1.5)
private val BlinkEvery = doubleArrayOf(3.4, 3.1, 3.3, 3.2)

// In the home they look left, then right.
private const val GLANCE = 6.0
private const val GLANCE_AT = 1.15
private const val GLANCE_TURN = 0.12
private const val GLANCE_HOLD = 0.25

// Surprise at a tap on the button: they stretch, start upwards, and only then shrink.
private const val STRETCH = 34.0 / 26.0
private const val STRETCH_IN = 0.15
private const val FLY_AFTER = 0.5
private const val SHRINK_AFTER = 0.6
private const val STRETCH_OUT = 0.25

// Pleased with the last page: two arcs for a moment.
private const val SMILE_AT = 0.15
private const val SMILE_IN = 0.12
private const val SMILE_FOR = 0.4

// A "no" for Later, a look down at a switch that flips, and a look towards a finger.
private const val SHAKE = 7.0
private const val SHAKE_FOR = 0.6
private const val SHAKE_TURNS = 2.0
private const val LOOK_DOWN = 3.0
private const val LOOK_DOWN_IN = 0.2
private const val LOOK_DOWN_FOR = 0.9
private const val LOOK_DOWN_OUT = 0.25
private const val LOOK = 4.0
private const val LOOK_REACH = 160.0
private const val LOOK_IN = 0.15
private const val LOOK_OUT = 0.4

// The chat leaves upwards and comes back from below.
private const val CHAT_OUT = 0.3
private const val CHAT_LIFT = 24.0
private const val CHAT_AFTER = 0.15
private const val CHAT_IN = 0.6
private const val CHAT_RISE = 28.0

// The ask: the message, the button and Later rise in one after the other; then the home grows round the eyes.
private const val ASK_AT = 0.9
private const val ASK_IN = 0.4
private const val ASK_GAP = 0.08
private const val ASK_RISE = 12.0
private const val ASK_OUT = 0.25
private const val HOUSE_AT = 1.7
private const val HOUSE_IN = 0.5
private const val HOUSE_FROM = 0.6

// The home: a house round the eyes' place, with rain falling inside it. The rain repeats every
// RAIN_PERIOD dp, which takes two seconds: a loop is a whole number of them.
private const val HOUSE_WIDTH = 170.0
private const val HOUSE_ABOVE = 105.0
private const val HOUSE_BELOW = 85.0
private const val HOUSE_ROOF = 56.0
private const val HOUSE_ROUND = 10.0
private const val RAIN_SPEED = 20.0
private const val RAIN_PERIOD = 40.0
private const val RAIN_PITCH = 16.0
private const val RAIN_STEP = 5.0
private const val RAIN_HEAD = 1.1
private const val RAIN_TAPER = 0.15
private val RainPhase = doubleArrayOf(0.0, 22.0, 9.0, 31.0, 15.0, 36.0, 4.0, 26.0, 12.0, 33.0)
private val RainStreak = intArrayOf(4, 3, 5, 3, 4, 5, 3, 4, 5, 3)

// The switches: the phone's outline draws itself from the notch, then its content comes in.
private const val SWITCHES_AT = 1.0
private const val OUTLINE_FOR = 0.6
private const val ITEM_GAP = 0.05
private const val ITEM_IN = 0.3
private const val ITEM_RISE = 6.0
private const val LEAVE = 0.3

// A knob slides over with a small overshoot. "All" flips the rows one after the other.
private val KnobSpring = Spring(stiffness = 300.0, damping = 22.0)
private const val KNOB = 0.25
private const val STAGGER = 0.09
private const val LABEL_FADE = 0.2

/** Every switch on: the last page follows this long after the last knob has landed. */
private const val DONE_AFTER = 0.4

// The last page: the switches shrink into the middle, the check pops and draws itself.
private const val SHRINK = 0.7
private const val SHRINK_FOR = 0.3
private const val POP_AT = 0.15
private const val POP_FROM = 0.5
private val Pop = Spring(stiffness = 200.0, damping = 13.0)
private const val POP_IN = 0.15
private const val DRAW_AT = 0.3
private const val DRAW_FOR = 0.3
private const val LABEL_AT = 0.45
private const val LABEL_IN = 0.3

// A tapped button gives a little.
private const val PRESS = 0.96
private const val PRESS_IN = 0.08
private const val PRESS_OUT = 0.24

// Reduced motion: a page fades out, the next one in, each as it looks when everything has arrived.
private const val FADE = 0.3
private const val SETTLED = 100.0

private class Spring(val stiffness: Double, val damping: Double)

/** What is drawn: the stage, the seconds into it, and how much of the pages shows. */
internal class Seen(val from: Int, val to: Int, val e: Double, val opacity: Double)

/**
 * The eyes: where their middle is ([dx] off the screen's), their size, the gap between their
 * centres, how far their glow reaches, how much of the large halo shows, their height as a share
 * of their width, and how much they are a smile.
 */
internal class EyesAt(
    val dx: Double,
    val y: Double,
    val size: Double,
    val gap: Double,
    val glow: Double,
    val halo: Double,
    val tall: Double,
    val smile: Double,
)

/** How much of a layer shows, and how far under its place it is. */
internal class LayerAt(val opacity: Double, val shift: Double)

internal class HouseAt(val opacity: Double, val scale: Double)

internal class OutlineAt(val opacity: Double, val drawn: Double)

internal class ItemAt(val opacity: Double, val rise: Double, val scale: Double)

internal class CheckAt(val opacity: Double, val scale: Double, val drawn: Double, val label: Double, val rise: Double)

internal fun clamp01(t: Double) = min(1.0, max(0.0, t))

private fun smooth(t: Double, from: Double, to: Double): Double {
    val u = clamp01((t - from) / (to - from))
    return u * u * (3 - 2 * u)
}

private fun easeOut(t: Double) = 1 - (1 - t) * (1 - t) * (1 - t)

private fun easeInOut(t: Double) = if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

/** How far a spring of mass 1 has come from 0 to 1 after [t] seconds; it overshoots a little. */
private fun spring(t: Double, spring: Spring): Double {
    if (t <= 0) return 0.0
    val w = sqrt(spring.stiffness)
    val z = spring.damping / (2 * w)
    val wd = w * sqrt(1 - z * z)
    return 1 - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
}

/** What is drawn at [now]: the stage and the seconds into it, and how much of the pages shows. */
internal fun seen(stage: Stage, now: Double, still: Boolean): Seen {
    val e = now - stage.t0
    if (!still) return Seen(stage.from, stage.to, e, 1.0)
    val page = if (e < FADE) stage.from else stage.to
    return Seen(page, page, SETTLED, if (e < FADE * 2) abs(e - FADE) / FADE else 1.0)
}

/** Whether the page takes taps yet, [e] seconds into its stage. */
internal fun ready(to: Int, e: Double) = e >= Ready[to]

/** How far the eyes are shut, 0 to 1. */
private fun blink(e: Double, first: Double, every: Double): Double {
    if (e < first) return 0.0
    val u = ((e - first) % every) / BLINK
    return if (u >= 1) 0.0 else 1 - abs(2 * u - 1)
}

/**
 * The eyes. [top], [hover] and [ask] are the heights of their three places. [still] leaves out
 * everything they do by themselves.
 */
internal fun eyes(from: Int, to: Int, e: Double, still: Boolean, top: Double, hover: Double, ask: Double): EyesAt {
    var large = 0.0
    var y = top
    if (to == ASK) {
        large = spring(e, Travel)
        y = top + (hover - top) * large + (ask - hover) * easeInOut(clamp01((e - DRIFT_AT) / DRIFT_FOR))
    } else if (from == ASK) {
        val startled = to == SWITCHES
        large = 1 - spring(e - (if (startled) SHRINK_AFTER else 0.0), Travel)
        y = ask + (top - ask) * spring(e - (if (startled) FLY_AFTER else 0.0), Travel)
    }
    var tall = 1.0
    var dx = 0.0
    var smile = 0.0
    if (!still) {
        tall = 1 - (1 - SHUT) * blink(e, BlinkFirst[to], BlinkEvery[to])
        if (to == ASK) {
            val right = GLANCE_AT + GLANCE_HOLD
            val back = right + GLANCE_HOLD
            dx = GLANCE * (2 * smooth(e, right, right + GLANCE_TURN) - smooth(e, GLANCE_AT, GLANCE_AT + GLANCE_TURN) - smooth(e, back, back + GLANCE_TURN))
        }
        if (from == ASK && to == SWITCHES) {
            tall *= 1 + (STRETCH - 1) * smooth(e, 0.0, STRETCH_IN) * (1 - smooth(e, SHRINK_AFTER, SHRINK_AFTER + STRETCH_OUT))
        }
        if (to == DONE) {
            smile = smooth(e, SMILE_AT, SMILE_AT + SMILE_IN) * (1 - smooth(e, SMILE_AT + SMILE_FOR, SMILE_AT + SMILE_FOR + SMILE_IN))
        }
    }
    return EyesAt(
        dx = dx,
        y = y,
        size = SMALL_SIZE + (LARGE_SIZE - SMALL_SIZE) * large,
        gap = SMALL_GAP + (LARGE_GAP - SMALL_GAP) * large,
        glow = SMALL_GLOW + (LARGE_GLOW - SMALL_GLOW) * large,
        halo = clamp01(large),
        tall = tall,
        smile = smile,
    )
}

/** The eyes' "no", [t] seconds after Later was tapped: how far to the side they are. */
internal fun shake(t: Double): Double {
    if (t < 0 || t > SHAKE_FOR) return 0.0
    val u = t / SHAKE_FOR
    return SHAKE * sin(2 * PI * SHAKE_TURNS * u) * (1 - u)
}

/** How far down the eyes look, [t] seconds after a switch was flipped. */
internal fun lookDown(t: Double) = LOOK_DOWN * smooth(t, 0.0, LOOK_DOWN_IN) * (1 - smooth(t, LOOK_DOWN_FOR, LOOK_DOWN_FOR + LOOK_DOWN_OUT))

/** How far the eyes look towards a finger that is [d] dp to one side of them. */
internal fun toward(d: Double) = LOOK * max(-1.0, min(1.0, d / LOOK_REACH))

/** How much of that look shows, [t] seconds after the finger came down ([held]) or left. */
internal fun follow(held: Boolean, t: Double) = if (held) smooth(t, 0.0, LOOK_IN) else 1 - smooth(t, 0.0, LOOK_OUT)

/** The chat: how much shows, and how far under its place it is. */
internal fun chat(from: Int, to: Int, e: Double): LayerAt {
    if (to == CHAT) {
        val u = clamp01((e - CHAT_AFTER) / CHAT_IN)
        return LayerAt(u, CHAT_RISE * (1 - easeOut(u)))
    }
    if (from == CHAT) {
        val u = clamp01(e / CHAT_OUT)
        return LayerAt(1 - u, -CHAT_LIFT * u * u)
    }
    return LayerAt(0.0, 0.0)
}

/** A part of the ask (0 the message, 1 the button, 2 Later): how much shows, and how far under its place it is. */
internal fun ask(from: Int, to: Int, e: Double, part: Int): LayerAt {
    if (to == ASK) {
        val u = clamp01((e - ASK_AT - part * ASK_GAP) / ASK_IN)
        return LayerAt(u, ASK_RISE * (1 - easeOut(u)))
    }
    return LayerAt(if (from == ASK) 1 - clamp01(e / ASK_OUT) else 0.0, 0.0)
}

/** The home round the eyes: how much shows, and its scale. */
internal fun house(from: Int, to: Int, e: Double): HouseAt {
    if (to == ASK) {
        val u = clamp01((e - HOUSE_AT) / HOUSE_IN)
        return HouseAt(u, HOUSE_FROM + (1 - HOUSE_FROM) * easeOut(u))
    }
    return HouseAt(if (from == ASK) 1 - clamp01(e / ASK_OUT) else 0.0, 1.0)
}

/** How far the rain has fallen at [now], on the screen's clock. */
internal fun rain(now: Double, still: Boolean) = if (still) 0.0 else (now * RAIN_SPEED) % RAIN_PERIOD

/** The phone's outline: how much shows, and how much of it is drawn. */
internal fun outline(from: Int, to: Int, e: Double): OutlineAt {
    if (to == SWITCHES) return OutlineAt(1.0, easeInOut(clamp01((e - SWITCHES_AT) / OUTLINE_FOR)))
    if (to == DONE) return OutlineAt(1.0, 1.0)
    return OutlineAt(if (from == SWITCHES || from == DONE) 1 - clamp01(e / LEAVE) else 0.0, 1.0)
}

/** A thing on the switches' page, [index] in the ITEM order: how much shows, how far under its place it is, and its scale about the check mark. */
internal fun item(from: Int, to: Int, e: Double, index: Int): ItemAt {
    if (to == SWITCHES) {
        val u = clamp01((e - SWITCHES_AT - index * ITEM_GAP) / ITEM_IN)
        return ItemAt(u, ITEM_RISE * (1 - easeOut(u)), 1.0)
    }
    if (from != SWITCHES) return ItemAt(0.0, 0.0, 1.0)
    val u = clamp01(e / SHRINK_FOR)
    return ItemAt(1 - u, 0.0, if (to == DONE) 1 + (SHRINK - 1) * u * u else 1.0)
}

/** The button under the phone, which stays for the last page: how much shows. */
internal fun pill(from: Int, to: Int, e: Double): Double {
    if (to == SWITCHES) return clamp01((e - SWITCHES_AT - ITEM_PILL * ITEM_GAP) / ITEM_IN)
    if (to == DONE) return 1.0
    return if (from == SWITCHES || from == DONE) 1 - clamp01(e / LEAVE) else 0.0
}

/** The check mark: how much shows, its scale, how much of the stroke is drawn, and its label. */
internal fun check(from: Int, to: Int, e: Double): CheckAt {
    if (to == DONE) {
        val label = clamp01((e - LABEL_AT) / LABEL_IN)
        return CheckAt(
            opacity = smooth(e, POP_AT, POP_AT + POP_IN),
            scale = POP_FROM + (1 - POP_FROM) * spring(e - POP_AT, Pop),
            drawn = easeOut(clamp01((e - DRAW_AT) / DRAW_FOR)),
            label = label,
            rise = ITEM_RISE * (1 - easeOut(label)),
        )
    }
    val left = if (from == DONE) 1 - clamp01(e / LEAVE) else 0.0
    return CheckAt(left, 1.0, 1.0, left, 0.0)
}

/** The switches: which are on, when each was last flipped, whether all are on and since when, when the last page is due, and the last touch of any. */
internal class Switches(
    val on: List<Boolean>,
    val at: List<Double>,
    val all: Boolean,
    val allAt: Double,
    val doneAt: Double,
    val flippedAt: Double,
)

internal const val ALL_ON = -1
internal const val ALL_OFF = -2

internal fun switchesOff() = Switches(
    on = List(COUNT) { false },
    at = List(COUNT) { -SETTLED },
    all = false,
    allAt = -SETTLED,
    doneAt = Double.POSITIVE_INFINITY,
    flippedAt = -SETTLED,
)

/** The switches after a flip of row [which], or of all of them, at [at]. A knob that is still sliding is left alone. */
internal fun flip(s: Switches, which: Int, at: Double): Switches {
    if (at < (if (which < 0) s.at.max() else s.at[which]) + KNOB) return s
    val on = s.on.toMutableList()
    val times = s.at.toMutableList()
    for (i in 0 until COUNT) {
        val next = if (which == ALL_ON) true else if (which == ALL_OFF) false else if (i == which) !on[i] else on[i]
        if (next != on[i]) {
            on[i] = next
            times[i] = at + (if (which < 0) i * STAGGER else 0.0)
        }
    }
    val all = on.all { it }
    return Switches(
        on = on,
        at = times,
        all = all,
        allAt = if (all == s.all) s.allAt else at,
        doneAt = if (all) times.max() + KNOB + DONE_AFTER else Double.POSITIVE_INFINITY,
        flippedAt = at,
    )
}

/** Where a switch's knob is, 0 off to 1 on, [t] seconds after its flip. */
internal fun knob(on: Boolean, t: Double, still: Boolean): Double {
    val p = if (still) (if (t >= 0) 1.0 else 0.0) else spring(t, KnobSpring)
    return if (on) p else 1 - p
}

/** How much the first row reads "Turn off all". */
internal fun allOn(s: Switches, now: Double): Double {
    val u = smooth(now - s.allAt, 0.0, LABEL_FADE)
    return if (s.all) u else 1 - u
}

/** A button's scale, [t] seconds after it was tapped. */
internal fun pressed(t: Double) = 1 - (1 - PRESS) * smooth(t, 0.0, PRESS_IN) * (1 - smooth(t, PRESS_IN, PRESS_IN + PRESS_OUT))

/** What a tap at (x, y) means on a page. [ground] is the height the buttons stand on. */
internal fun hit(page: Int, x: Double, y: Double, width: Double, height: Double, ground: Double): Int {
    val side = abs(x - width / 2)
    val middle = height / 2
    if (page == CHAT) return TAP_CHAT
    if (page == ASK) {
        if (side <= width / 2 - ROW_SIDE && y >= ground - ROW_BOTTOM - ROW_HEIGHT && y <= ground - ROW_BOTTOM) return TAP_START
        if (side <= LATER_REACH && abs(y - (ground - LATER_BOTTOM)) <= LATER_HEIGHT / 2) return TAP_LATER
        return TAP_NONE
    }
    if (side <= width / 2 - PILL_SIDE && y >= ground - PILL_BOTTOM - PILL_HEIGHT && y <= ground - PILL_BOTTOM) return TAP_PILL
    if (page == SWITCHES && side <= LIST_WIDTH / 2) {
        if (y >= middle + ALL_TOP && y <= middle + ALL_TOP + ALL_HEIGHT) return TAP_ALL
        val row = kotlin.math.floor((y - middle - LIST_TOP + LIST_PITCH / 2) / LIST_PITCH).toInt()
        if (row in 0 until COUNT) return TAP_SWITCH + row
    }
    return TAP_NONE
}

// The drawings. Each stack turns the same numbers into its own kind of path.

/** The home's corners round the eyes' place, clockwise from the roof's tip; y grows downwards. */
private val HouseCorners = listOf(
    Offset(0f, -HOUSE_ABOVE.toFloat()),
    Offset((HOUSE_WIDTH / 2).toFloat(), (HOUSE_ROOF - HOUSE_ABOVE).toFloat()),
    Offset((HOUSE_WIDTH / 2).toFloat(), HOUSE_BELOW.toFloat()),
    Offset((-HOUSE_WIDTH / 2).toFloat(), HOUSE_BELOW.toFloat()),
    Offset((-HOUSE_WIDTH / 2).toFloat(), (HOUSE_ROOF - HOUSE_ABOVE).toFloat()),
)

/** The home's outline: every corner is cut back along both of its sides and curved. */
internal fun housePath() = Path().apply {
    val n = HouseCorners.size
    for (i in 0 until n) {
        val p = HouseCorners[i]
        val before = HouseCorners[(i + n - 1) % n]
        val after = HouseCorners[(i + 1) % n]
        val a = HOUSE_ROUND.toFloat() / hypot(before.x - p.x, before.y - p.y)
        val b = HOUSE_ROUND.toFloat() / hypot(after.x - p.x, after.y - p.y)
        val cut = p + (before - p) * a
        if (i == 0) moveTo(cut.x, cut.y) else lineTo(cut.x, cut.y)
        val end = p + (after - p) * b
        quadraticTo(p.x, p.y, end.x, end.y)
    }
    close()
}

/** The rain's drops round the eyes' place: streaks in columns, with a period to spare above the home. */
internal fun rainPath() = Path().apply {
    val columns = RainPhase.size
    for (column in 0 until columns) {
        val x = (column - (columns - 1) / 2.0) * RAIN_PITCH
        var y = RainPhase[column] - HOUSE_ABOVE - RAIN_PERIOD
        while (y < HOUSE_BELOW + RAIN_STEP * RainStreak[column]) {
            for (drop in 0 until RainStreak[column]) {
                val r = (RAIN_HEAD - drop * RAIN_TAPER).toFloat()
                val middle = Offset(x.toFloat(), (y - drop * RAIN_STEP).toFloat())
                addOval(Rect(middle, r))
            }
            y += RAIN_PERIOD
        }
    }
}

/** The right half of the phone's outline, from the middle of its notch out and down to [bottom]. */
internal fun outlinePath(middle: Double, top: Double, bottom: Double) = Path().apply {
    val m = middle.toFloat()
    val t = top.toFloat()
    val notch = PHONE_NOTCH.toFloat()
    val depth = PHONE_DEPTH.toFloat()
    val fillet = PHONE_FILLET.toFloat()
    val half = PHONE_HALF.toFloat()
    val radius = PHONE_RADIUS.toFloat()
    moveTo(m, t + depth)
    lineTo(m + notch - fillet, t + depth)
    quadraticTo(m + notch, t + depth, m + notch, t + depth - fillet)
    quadraticTo(m + notch, t, m + notch + fillet, t)
    lineTo(m + half - radius, t)
    quadraticTo(m + half, t, m + half, t + radius)
    lineTo(m + half, bottom.toFloat())
}

// A heart in a box from -1 to 1: where it starts, and its curves as two control points and an end.
private val HeartStart = Offset(0f, 0.9f)
private val HeartCurves = listOf(
    floatArrayOf(-0.25f, 0.68f, -1f, 0.25f, -1f, -0.3f),
    floatArrayOf(-1f, -0.68f, -0.72f, -0.9f, -0.45f, -0.9f),
    floatArrayOf(-0.25f, -0.9f, -0.08f, -0.78f, 0f, -0.62f),
    floatArrayOf(0.08f, -0.78f, 0.25f, -0.9f, 0.45f, -0.9f),
    floatArrayOf(0.72f, -0.9f, 1f, -0.68f, 1f, -0.3f),
    floatArrayOf(1f, 0.25f, 0.25f, 0.68f, 0f, 0.9f),
)

internal fun heartPath() = Path().apply {
    moveTo(HeartStart.x, HeartStart.y)
    for (c in HeartCurves) cubicTo(c[0], c[1], c[2], c[3], c[4], c[5])
    close()
}

/** The check mark's three corners round its middle, and its stroke. */
private val Check = floatArrayOf(-17f, 0f, -6f, 11f, 17f, -12f)
internal const val CHECK_STROKE = 2.5f

internal fun checkPath() = Path().apply {
    moveTo(Check[0], Check[1])
    lineTo(Check[2], Check[3])
    lineTo(Check[4], Check[5])
}

/** A smiling eye, one unit wide: an arc from corner to corner over a control point, and its stroke. */
private val Smile = floatArrayOf(-0.5f, 0.18f, 0f, -0.55f, 0.5f, 0.18f)
internal const val SMILE_STROKE = 0.26f

internal fun smilePath() = Path().apply {
    moveTo(Smile[0], Smile[1])
    quadraticTo(Smile[2], Smile[3], Smile[4], Smile[5])
}
