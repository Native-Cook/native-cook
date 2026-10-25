package com.ngocdevv.nativecook.blocks.forms.magneticlettersname

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Magnetic Letters Name — the timeline and the flight of one magnet, as pure functions of time
 (the React Native magneticLettersMotion.ts, unchanged).

 A magnet's pose is where its centre is (dp), its turn (rad), how far it is lifted off the door
 (0 = flat on the steel, 1 = held a finger's width away) and a uniform scale (the snap's bounce).
 autoPlay asks `magAutoPose(t)` for every magnet; under touch each magnet keeps a flight record
 (from, to, start, kind) and the same curves are asked with the time since that flight began.
*/

/** The loop, in seconds. */
internal const val LOOP = 9f
/** The name the loop spells. */
internal const val MAG_SCRIPT = "NOAH"
/** Typing: the first letter leaves the pile at 1.3 s, one more every 0.42 s, each flight 0.38 s. */
internal const val MAG_TYPE_START = 1.3f
internal const val MAG_TYPE_STAGGER = 0.42f
internal const val MAG_FLY = 0.38f
/** "Continue" lights up at 3.6 s (0.25 s) and goes out with the first backspace. */
internal const val MAG_READY_AT = 3.6f
/** Backspace: the last letter pops off at 5.2 s, one more every 0.45 s, each flight 0.45 s. */
internal const val MAG_POP_START = 5.2f
internal const val MAG_POP_STAGGER = 0.45f
internal const val MAG_POP = 0.45f
/** The longest name the line holds. */
internal const val MAG_MAX_LETTERS = 10
/** A drop after a drag: 0.2 s to fall back onto the steel. */
internal const val MAG_DROP = 0.2f

internal const val MAG_KIND_REST = 0
internal const val MAG_KIND_SNAP = 1
internal const val MAG_KIND_LAND = 2
internal const val MAG_KIND_DROP = 3
internal const val MAG_KIND_HELD = 4

internal class MagPose(
    var x: Float = 0f,
    var y: Float = 0f,
    var rot: Float = 0f,
    var lift: Float = 0f,
    var scale: Float = 1f,
) {
    fun set(o: MagPose): MagPose {
        x = o.x; y = o.y; rot = o.rot; lift = o.lift; scale = o.scale
        return this
    }

    fun copy() = MagPose(x, y, rot, lift, scale)

    /** A resting pose: this pose's place and turn, flat on the steel. */
    fun flat() = MagPose(x, y, rot, 0f, 1f)
}

internal fun magClamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v

internal fun magEaseInOutCubic(v: Float): Float {
    val x = magClamp01(v)
    return if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
}

internal fun magEaseOutCubic(v: Float): Float {
    val x = magClamp01(v)
    return 1 - (1 - x).pow(3)
}

/** The tiny bounce after contact: the scale dips ~5 % within 40 ms, overshoots ~2 %, and settles. */
internal fun magContactBounce(since: Float): Float {
    if (since <= 0f || since > 0.7f) return 1f
    return 1 - 0.06f * exp(-since * 11) * sin(since * 38)
}

/** How a magnet's turn approaches 0 so it never spins the long way round. */
private fun shortAngle(a: Float): Float {
    var v = a
    val tau = (PI * 2).toFloat()
    while (v > PI) v -= tau
    while (v < -PI) v += tau
    return v
}

private fun landed(to: MagPose, u: Float, dur: Float, out: MagPose): MagPose {
    val b = magContactBounce(u - dur)
    out.x = to.x; out.y = to.y + (1 - b) * 20; out.rot = to.rot; out.lift = 0f; out.scale = b
    return out
}

/**
 * Magnetic snap from [from] onto [to] (a slot on the name line). For the first 80 % of the flight
 * the magnet lifts and glides in an arc, slowing as it nears; in the last 20 dp the door's pull
 * takes it and it accelerates into place, then bounces. [u] is seconds since the flight began.
 */
internal fun magSnapPose(from: MagPose, to: MagPose, u: Float, dur: Float, out: MagPose): MagPose {
    val tau = u / dur
    if (tau <= 0f) return out.set(from)
    if (tau >= 1f) return landed(to, u, dur, out)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dist = max(1f, sqrt(dx * dx + dy * dy))
    val pull = min(20f, dist * 0.3f) / dist
    val kA = 0.8f
    val s = if (tau < kA) {
        val x = tau / kA
        // Mostly an ease-in-out, kept 30 % linear so it is still moving when the pull takes over.
        (1 - pull) * (0.7f * x * x * (3 - 2 * x) + 0.3f * x)
    } else {
        val q = (tau - kA) / (1 - kA)
        val vA = ((1 - pull) * 0.3f) / kA
        val v0 = magClamp01((vA * (1 - kA)) / max(pull, 0.0001f))
        1 - pull + pull * (v0 * q + (1 - v0) * q * q)
    }
    val arc = min(64f, dist * 0.28f)
    val lift = if (tau < 0.2f) magEaseOutCubic(tau / 0.2f) else if (tau < kA) 1f else 1 - (tau - kA) / (1 - kA)
    val e = magEaseOutCubic(tau / 0.85f)
    val rot = shortAngle(from.rot) * (1 - e) + to.rot * e
    out.x = from.x + dx * s
    out.y = from.y + dy * s - arc * sin(PI.toFloat() * s)
    out.rot = rot
    out.lift = max(lift, from.lift * (1 - tau))
    out.scale = 1 + 0.06f * out.lift
    return out
}

/**
 * Popping off: the magnet presses in (60 ms), jumps free of the line, flies back in an arc
 * (ease-in-out) and lands on the pile with the same bounce.
 */
internal fun magLandPose(from: MagPose, to: MagPose, u: Float, dur: Float, out: MagPose): MagPose {
    val tau = u / dur
    if (tau <= 0f) return out.set(from)
    if (tau >= 1f) return landed(to, u, dur, out)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dist = sqrt(dx * dx + dy * dy)
    val press = 0.13f
    if (tau < press) {
        val k = sin(PI.toFloat() * tau / press)
        out.x = from.x; out.y = from.y + k * 1.5f; out.rot = from.rot; out.lift = from.lift
        out.scale = 1 - 0.03f * k
        return out
    }
    val x = (tau - press) / (1 - press)
    val s = magEaseInOutCubic(x)
    val arc = min(70f, 26 + dist * 0.22f)
    val lift = if (x < 0.25f) magEaseOutCubic(x / 0.25f) else if (x < 0.8f) 1f else 1 - (x - 0.8f) / 0.2f
    out.x = from.x + dx * s
    out.y = from.y + dy * s - arc * sin(PI.toFloat() * min(1f, s * 1.08f))
    out.rot = from.rot + (to.rot - from.rot) * s + sin(PI.toFloat() * s) * 0.35f
    out.lift = max(lift, from.lift * (1 - x))
    out.scale = 1 + 0.07f * out.lift
    return out
}

/** Let go away from the line: the magnet falls back onto the steel (accelerating, like a drop) and bounces. */
internal fun magDropPose(from: MagPose, u: Float, out: MagPose): MagPose {
    val tau = u / MAG_DROP
    out.x = from.x; out.rot = from.rot
    if (tau >= 1f) {
        val b = magContactBounce(u - MAG_DROP)
        out.y = from.y + (1 - b) * 20; out.lift = 0f; out.scale = b
    } else {
        out.y = from.y
        out.lift = from.lift * (1 - tau * tau)
        out.scale = 1 + 0.06f * out.lift
    }
    return out
}

/** Where script letter k is in the loop: 0 on the pile, 1 flying in / on the line, 2 popping off. */
internal fun magAutoPhase(k: Int, t: Float): Int {
    val typeAt = MAG_TYPE_START + MAG_TYPE_STAGGER * k
    val popAt = MAG_POP_START + MAG_POP_STAGGER * (MAG_SCRIPT.length - 1 - k)
    return if (t < typeAt) 0 else if (t < popAt) 1 else 2
}

/** Seconds since script letter k entered its current phase (0 on the pile). */
internal fun magAutoSince(k: Int, t: Float): Float {
    val typeAt = MAG_TYPE_START + MAG_TYPE_STAGGER * k
    val popAt = MAG_POP_START + MAG_POP_STAGGER * (MAG_SCRIPT.length - 1 - k)
    return if (t < typeAt) 0f else if (t < popAt) t - typeAt else t - popAt
}

/** "Continue" in the loop: lit from 3.6 s to the first backspace. */
internal fun magAutoReady(t: Float) =
    magEaseOutCubic((t - MAG_READY_AT) / 0.25f) * (1 - magEaseOutCubic((t - MAG_POP_START) / 0.2f))

/** How many letters the loop's line holds at t (counted when each lands). */
internal fun magAutoCount(t: Float): Int {
    var n = 0
    for (k in MAG_SCRIPT.indices) {
        if (magAutoPhase(k, t) == 1 && magAutoSince(k, t) >= MAG_FLY) n += 1
    }
    return n
}
