package com.ngocdevv.nativecook.blocks.media.bubblemediaburst

import androidx.compose.animation.core.CubicBezierEasing
import com.ngocdevv.nativecook.shared.EaseOutStrong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** One loop of the autoPlay, in seconds. */
internal const val LOOP = 7.0

// The autoPlay's timeline, in seconds from the start of a loop: "+" goes down and comes up (which
// opens the bubbles), one photo is picked and let go, then "+" is pressed again (which closes them).
internal const val OPEN_PRESS = 0.8
internal const val PRESS_FOR = 0.12
internal const val PICK_AT = 3.2
internal const val UNPICK_AT = 4.2
internal const val CLOSE_PRESS = 4.8

/** The bubble the autoPlay picks: the second one. */
internal const val AUTO_PICK = 1

/** Seconds after the last touch before the autoPlay takes over again. */
internal const val USER_HOLD = 3.0

internal class Spring(val mass: Double, val stiffness: Double, val damping: Double)

// Where a bubble is along its path (a little past the end, then back), and how big it is
// (from a fifth of its size to about 1.09 and back to 1).
internal val TRAVEL = Spring(1.0, 170.0, 18.0)
internal val GROW = Spring(1.0, 220.0, 17.0)

/** The "+" turning into an "×" and back. */
internal val TURN = Spring(1.0, 260.0, 22.0)

/** A picked bubble swelling and its check popping in. */
internal val POP = Spring(1.0, 320.0, 17.0)

/** The "+" coming back up after a press. */
internal val RELEASE = Spring(1.0, 380.0, 20.0)

/** A bubble is born at this share of its size, behind the "+". */
internal const val BORN_SCALE = 0.2

/** Seconds a new bubble takes to become opaque, so it never pops in. */
internal const val BORN_FADE = 0.08

/** Seconds after its birth over which a bubble eases into its float. */
internal const val FLOAT_IN = 0.8

/** How far a path bows to the left of the straight line, as a share of its length. */
internal const val CURVE = 0.18f

/** Seconds each bubble takes to fall back into the "+". */
internal const val FALL = 0.35

/** Seconds the "+" takes to go down when pressed, and how far it goes. */
internal const val PRESS_IN = 0.1
internal const val PRESS_SCALE = 0.92

/** The give of the "+" when a bubble lands in it: down, then back up. */
internal const val PULSE_IN = 0.07
internal const val PULSE_OUT = 0.28

/** A picked bubble's size, and how long it takes to shrink back when let go. */
internal const val PICK_SCALE = 1.06
internal const val UNPICK_FOR = 0.22
internal const val TURN_ANGLE = 45.0

/** Reduced motion: every change is a fade of this length, in place. */
internal const val FADE = 0.2

/** A falling bubble starts slowly and drops into the "+" fast. */
internal val SLIDE = CubicBezierEasing(0.55f, 0.085f, 0.68f, 0.53f)

/** It keeps most of its size until it is well on its way. */
internal val SHRINK = CubicBezierEasing(0.5f, 0f, 0.75f, 0.6f)

/**
 * Where a bubble rests and how it floats, at cluster scale 1: its centre from the centre of the "+"
 * (x to the right, y up, in dp), its size, the seconds after the "+" is released that it is born and
 * that it starts to fall, its float (dp each way and seconds a period on each axis, and where it
 * starts, in radians), and how far the "+" gives when it lands in it.
 */
internal class Slot(
    val x: Float,
    val y: Float,
    val size: Float,
    val openDelay: Double,
    val closeDelay: Double,
    val floatX: Double,
    val floatY: Double,
    val periodX: Double,
    val periodY: Double,
    val phase: Double,
    val dip: Double,
)

// Born big one first, then the mushroom, the flower, the burst and the tiny one; they fall back in
// the order big, tiny, mushroom, flower, burst.
internal val SLOTS = listOf(
    Slot(53f, 126f, 165f, 0.0, 0.0, 1.0, 3.0, 3.9, 3.4, 0.0, 0.08),
    Slot(-81f, 157f, 95f, 0.12, 0.45, 1.5, 3.0, 3.3, 2.8, 1.9, 0.06),
    Slot(-22f, 232f, 76f, 0.06, 0.3, 1.2, 3.0, 3.6, 3.1, 4.0, 0.05),
    Slot(-60f, 66f, 76f, 0.18, 0.6, 1.5, 2.5, 3.0, 2.6, 2.7, 0.05),
    Slot(36f, 230f, 25f, 0.24, 0.15, 4.0, 5.0, 3.4, 3.0, 5.2, 0.03),
)

/** The cluster's width and height above the centre of the "+" at scale 1, float included. */
internal const val CLUSTER_WIDTH = 272f
internal const val CLUSTER_HEIGHT = 274f

/** Seconds from the "+" closing the cluster until everything is still. */
internal const val SETTLE = 0.6 + FALL + PULSE_IN + PULSE_OUT

internal const val NEVER = -1e6

/** Everything that happened, as times on the clock in seconds. */
internal data class Events(
    val openAt: Double = NEVER,
    val closeAt: Double = NEVER,
    val downAt: Double = NEVER,
    val upAt: Double = NEVER,
    val pickOn: List<Boolean>,
    val pickAt: List<Double>,
) {
    val isOpen get() = openAt > closeAt
}

internal fun restEvents(count: Int) = Events(pickOn = List(count) { false }, pickAt = List(count) { NEVER })

/** A slot's place on screen, in dp: its centre at rest, the bend of its path, and the centre of the "+". */
internal class Place(val restX: Float, val restY: Float, val ctrlX: Float, val ctrlY: Float, val bx: Float, val by: Float)

internal class Pose(val x: Float, val y: Float, val scale: Float, val opacity: Float)

private fun clamp01(value: Double) = value.coerceIn(0.0, 1.0)

private fun ease(curve: CubicBezierEasing, t: Double) = curve.transform(t.toFloat()).toDouble()

private fun easeOut(t: Double) = EaseOutStrong.transform(t.toFloat()).toDouble()

/** A spring let go from 0 towards 1 at rest, [t] seconds later (the closed form, so every stack agrees). */
internal fun spring(t: Double, s: Spring): Double {
    if (t <= 0) return 0.0
    val w0 = sqrt(s.stiffness / s.mass)
    val zeta = s.damping / (2 * sqrt(s.stiffness * s.mass))
    if (zeta >= 1) return 1 - exp(-w0 * t) * (1 + w0 * t)
    val wd = w0 * sqrt(1 - zeta * zeta)
    return 1 - exp(-zeta * w0 * t) * (cos(wd * t) + (zeta * w0 / wd) * sin(wd * t))
}

private fun smoothstep(x: Double): Double {
    val t = clamp01(x)
    return t * t * (3 - 2 * t)
}

/** The time of an event at [offset] in a loop: this loop's if it has happened, else the last loop's. */
private fun last(base: Double, tau: Double, offset: Double) = if (tau >= offset) base + offset else base + offset - LOOP

/** The events of the autoPlay at [now] for a loop that started at [start]. */
internal fun autoEvents(now: Double, start: Double, count: Int): Events {
    val tau = (now - start) % LOOP
    val base = now - tau
    val opened = last(base, tau, OPEN_PRESS + PRESS_FOR)
    val closed = last(base, tau, CLOSE_PRESS + PRESS_FOR)
    val picked = last(base, tau, PICK_AT)
    val unpicked = last(base, tau, UNPICK_AT)
    return Events(
        openAt = opened,
        closeAt = closed,
        downAt = max(last(base, tau, OPEN_PRESS), last(base, tau, CLOSE_PRESS)),
        upAt = max(opened, closed),
        pickOn = List(count) { it == AUTO_PICK && picked > unpicked },
        pickAt = List(count) { if (it == AUTO_PICK) max(picked, unpicked) else NEVER },
    )
}

/** Where a bubble is, how big and how opaque, at [now]. */
internal fun pose(slot: Slot, place: Place, now: Double, openAt: Double, closeAt: Double, still: Boolean): Pose {
    val hidden = Pose(place.bx, place.by, 0f, 0f)
    val closing = closeAt >= openAt
    if (closing && now - closeAt >= (if (still) FADE else slot.closeDelay + FALL)) return hidden
    val since = now - openAt
    var x = place.restX.toDouble()
    var y = place.restY.toDouble()
    var scale = 1.0
    var opacity: Double
    if (still) {
        opacity = clamp01(since / FADE)
    } else {
        val t = since - slot.openDelay
        if (t <= 0) return hidden
        // Along a quadratic curve from the "+" to its place, a little past it and back.
        val u = spring(t, TRAVEL)
        val v = 1 - u
        x = v * v * place.bx + 2 * v * u * place.ctrlX + u * u * place.restX
        y = v * v * place.by + 2 * v * u * place.ctrlY + u * u * place.restY
        val float = smoothstep(t / FLOAT_IN)
        x += float * slot.floatX * sin(2 * PI * since / slot.periodX + slot.phase)
        y += float * slot.floatY * sin(2 * PI * since / slot.periodY + slot.phase)
        scale = BORN_SCALE + (1 - BORN_SCALE) * spring(t, GROW)
        opacity = clamp01(t / BORN_FADE)
    }
    if (closing) {
        if (still) {
            opacity *= 1 - clamp01((now - closeAt) / FADE)
        } else {
            val c = clamp01((now - closeAt - slot.closeDelay) / FALL)
            val k = ease(SLIDE, c)
            x += (place.bx - x) * k
            y += (place.by - y) * k
            scale *= 1 - ease(SHRINK, c)
        }
    }
    return Pose(x.toFloat(), y.toFloat(), scale.toFloat(), opacity.toFloat())
}

/** How picked a bubble looks: 0 to 1, with an overshoot when it is picked. */
internal fun pickAmount(on: Boolean, at: Double, now: Double, still: Boolean): Double {
    val t = now - at
    if (on) return if (still) clamp01(t / FADE) else spring(t, POP)
    return 1 - (if (still) clamp01(t / FADE) else easeOut(clamp01(t / UNPICK_FOR)))
}

/** The size of the "+": down while pressed, springing back, and giving as each bubble lands. */
internal fun buttonScale(e: Events, now: Double, still: Boolean): Double {
    val press = if (e.downAt > e.upAt) {
        easeOut(clamp01((now - e.downAt) / PRESS_IN))
    } else {
        easeOut(clamp01((e.upAt - e.downAt) / PRESS_IN)) * (1 - spring(now - e.upAt, RELEASE))
    }
    var dip = 0.0
    if (!still && e.closeAt >= e.openAt) {
        for (slot in SLOTS) {
            val d = now - e.closeAt - slot.closeDelay - FALL
            if (d < 0 || d > PULSE_IN + PULSE_OUT) continue
            val amount = if (d < PULSE_IN) easeOut(d / PULSE_IN) else 1 - ease(SHRINK, (d - PULSE_IN) / PULSE_OUT)
            dip = max(dip, amount * slot.dip)
        }
    }
    return (1 - (1 - PRESS_SCALE) * press) * (1 - dip)
}

/** The turn of the "+", in degrees: 45 while open. */
internal fun plusTurn(e: Events, now: Double, still: Boolean): Double {
    if (e.isOpen) return if (still) TURN_ANGLE else TURN_ANGLE * spring(now - e.openAt, TURN)
    return if (still) 0.0 else TURN_ANGLE * (1 - spring(now - e.closeAt, TURN))
}
