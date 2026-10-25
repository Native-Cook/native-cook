package com.ngocdevv.nativecook.blocks.modals.waxsealinvite

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 The Wax Seal Invite's clock (the React Native waxSealMotion.ts). Every value is a closed-form
 function of the loop time t (seconds), so t = LOOP draws the frame of t = 0. Lengths in dp,
 angles in radians.

   0.0 – 1.6   rest: the sealed envelope; a gleam of light slides across the wax (0.2 – 1.4).
   1.6 – 2.4   a thumb presses the seal (scale 0.95) and pulls it down 22 dp (rubber-banded).
   2.4 – 3.2   seven cracks run out from the middle, staggered 40 ms.
   3.2 – 4.2   the seal breaks: its seven pieces pop out and fall, turning, under gravity.
   4.2 – 5.2   the flap opens about its top edge (ease-in-out), dark as it goes edge-on, its red
               liner lit once it is open.
   5.2 – 6.0   the card rises out of the envelope (5.2 – 5.7), comes forward over it and settles,
               growing to 1.1× with a small overshoot (5.7 – 6.0); the RSVP buttons fade in.
   6.0 – 7.6   hold.
   7.6 – 10    in reverse, one step after another: the buttons fade (7.6 – 7.8); the card lifts
               off the envelope and goes behind it (7.7 – 8.1), then slides down into it
               (8.1 – 8.6); the flap closes (8.6 – 9.2); only then the pieces fly back onto the
               closed flap along their own paths (9.2 – 9.75) and the cracks heal
               (9.65 – 9.9); rest (9.9 – 10).
 */

const val LOOP = 10f
private const val TAU = PI.toFloat() * 2f
internal const val WAX_PI = PI.toFloat()

internal fun waxClamp01(x: Float) = min(1f, max(0f, x))

internal fun waxSeg(t: Float, a: Float, b: Float) = waxClamp01((t - a) / (b - a))

internal fun waxEaseOut(x: Float): Float {
    val u = 1f - waxClamp01(x)
    return 1f - u * u * u
}

internal fun waxEaseInOut(x: Float): Float {
    val u = waxClamp01(x)
    return if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
}

/** Ease-out with a small overshoot (about 5 %). */
internal fun waxEaseOutBack(x: Float): Float {
    val u = waxClamp01(x) - 1f
    val s = 1.2f
    return 1f + u * u * ((s + 1f) * u + s)
}

internal fun waxHash(i: Int): Float {
    val s = sin(i * 12.9898 + 4.37) * 43758.5453
    return (s - floor(s)).toFloat()
}

internal fun waxRubber(x: Float, limit: Float) = limit * (1f - 1f / (1f + max(0f, x) / limit))

/** How long the pieces fall before they are gone (s). */
internal const val WAX_FALL = 1.0f
/** The scripted pull on the seal (dp, before the rubber band). */
private const val PULL = 70f
internal const val WAX_PULL_LIMIT = 30f

internal class WaxPose {
    /** Seal pressed (0…1) and pulled down (dp, already rubber-banded). */
    var press = 0f
    var pull = 0f
    /** Cracks grown (0…1), and seconds the pieces have been falling (0 = whole). */
    var crack = 0f
    var fall = 0f
    /** The flap's angle (0 closed … π open). */
    var flap = 0f
    /** The card: 0 inside, 1 just clear above the envelope, 2 settled in front. */
    var card = 0f
    /** RSVP buttons shown (0…1). */
    var buttons = 0f
    /** The gleam's place across the seal (−1.5 … 1.5; off the seal at both ends). */
    var gleam = -1.5f

    val broken get() = crack > 0.001f || fall > 0f
}

internal fun WaxPose.setRest() {
    press = 0f; pull = 0f; crack = 0f; fall = 0f; flap = 0f; card = 0f; buttons = 0f; gleam = -1.5f
}

internal fun WaxPose.setLoop(t: Float) {
    gleam = -1.5f + 3f * waxEaseInOut(waxSeg(t, 0.2f, 1.4f))
    // Press and pull; let go as the pieces fall, and carried back to rest by the reform.
    val grip = waxEaseInOut(waxSeg(t, 1.6f, 2.4f))
    val back = waxEaseInOut(waxSeg(t, 9.2f, 9.75f))
    press = grip - waxEaseOut(waxSeg(t, 3.2f, 3.5f)) * grip
    pull = waxRubber(PULL * grip, WAX_PULL_LIMIT) * (1f - back)
    // Cracks out, then healed.
    crack = waxEaseOut(waxSeg(t, 2.4f, 3.1f)) * (1f - waxEaseInOut(waxSeg(t, 9.65f, 9.9f)))
    // The fall, and the same path flown backwards.
    fall = when {
        t >= 3.2f && t < 9.2f -> min(WAX_FALL, t - 3.2f)
        t >= 9.2f -> WAX_FALL * (1f - back)
        else -> 0f
    }
    flap = WAX_PI * (waxEaseInOut(waxSeg(t, 4.2f, 5.2f)) - waxEaseInOut(waxSeg(t, 8.6f, 9.2f)))
    card = when {
        t >= 5.2f && t < 7.7f -> waxEaseInOut(waxSeg(t, 5.2f, 5.7f)) + waxEaseOutBack(waxSeg(t, 5.7f, 6.05f))
        t >= 7.7f -> 2f - waxEaseInOut(waxSeg(t, 7.7f, 8.1f)) - waxEaseInOut(waxSeg(t, 8.1f, 8.6f))
        else -> 0f
    }
    buttons = waxEaseOut(waxSeg(t, 5.85f, 6.2f)) * (1f - waxEaseOut(waxSeg(t, 7.6f, 7.8f)))
}

/** The invitation open and still (reduced motion). */
internal fun WaxPose.setOpen() {
    press = 0f; pull = 0f; crack = 1f; fall = WAX_FALL; flap = WAX_PI; card = 2f; buttons = 1f; gleam = -1.5f
}

// The cracks: CRACKS polylines of CRACK_POINTS points from the seal's centre outward, past the
// wax's edge. Seeded, so they are the same every loop.
internal const val WAX_CRACKS = 7
internal const val WAX_CRACK_POINTS = 6

/** The cracks' points (seal-centred), crack by crack, as x, y pairs. */
internal fun waxMakeCracks(r0: Float): Array<FloatArray> = Array(WAX_CRACKS) { i ->
    val a = i.toFloat() / WAX_CRACKS * TAU + (waxHash(i + 1) - 0.5f) * 0.5f - WAX_PI / 2f
    val pts = FloatArray(WAX_CRACK_POINTS * 2)
    for (j in 0 until WAX_CRACK_POINTS) {
        val f = j.toFloat() / (WAX_CRACK_POINTS - 1)
        val r = r0 * 1.3f * f
        val side = if (j == 0 || j == WAX_CRACK_POINTS - 1) 0f else (waxHash(i * 7 + j + 3) - 0.5f) * r0 * 0.16f
        pts[j * 2] = cos(a) * r - sin(a) * side
        pts[j * 2 + 1] = sin(a) * r + cos(a) * side
    }
    pts
}

/** Piece i's middle direction (radians). */
internal fun waxPieceAngle(cracks: Array<FloatArray>, i: Int): Float {
    val a = cracks[i]
    val b = cracks[(i + 1) % WAX_CRACKS]
    val a0 = atan2(a[a.size - 1], a[a.size - 2])
    var a1 = atan2(b[b.size - 1], b[b.size - 2])
    if (a1 < a0) a1 += TAU
    return (a0 + a1) / 2f
}

/** Piece i after falling `fall` s, written into out: dx, dy, rotation (rad), pivot x, pivot y (seal-centred). */
internal fun waxPieceMotion(i: Int, mid: Float, fall: Float, r: Float, out: FloatArray) {
    val px = cos(mid) * r * 0.55f
    val py = sin(mid) * r * 0.55f
    out[3] = px
    out[4] = py
    if (fall <= 0f) {
        out[0] = 0f; out[1] = 0f; out[2] = 0f
        return
    }
    val speed = 45f + waxHash(i + 20) * 55f
    val vx = cos(mid) * speed
    val vy = sin(mid) * speed * 0.6f - 90f - waxHash(i + 21) * 50f
    val g = 1500f
    out[0] = vx * fall
    out[1] = vy * fall + 0.5f * g * fall * fall
    out[2] = (waxHash(i + 22) - 0.5f) * 7f * fall
}

/** A value animated as a pure function of the screen clock: a timing curve, or a spring. */
internal class WaxTrack(initial: Float) {
    private var from = initial
    private var to = initial
    private var start = 0f
    private var duration = 0f
    private var easing: (Float) -> Float = { it }
    private var spring = false
    private var omega = 0f
    private var zeta = 0f

    val target get() = to

    fun set(v: Float) {
        from = v; to = v; duration = 0f; spring = false
    }

    fun timing(now: Float, target: Float, seconds: Float, ease: (Float) -> Float) {
        from = value(now); to = target; start = now; duration = seconds; easing = ease; spring = false
    }

    /** Like Reanimated's duration-based spring: ω = 2π / duration. */
    fun spring(now: Float, target: Float, seconds: Float, dampingRatio: Float) {
        from = value(now); to = target; start = now; spring = true
        omega = 2f * WAX_PI / seconds; zeta = dampingRatio
        duration = seconds * 2.5f
    }

    fun value(now: Float): Float {
        if (spring) {
            val u = now - start
            if (u <= 0f) return from
            val a = from - to
            val wd = omega * sqrt(1f - zeta * zeta)
            val b = zeta * omega * a / wd
            return to + exp(-zeta * omega * u) * (a * cos(wd * u) + b * sin(wd * u))
        }
        if (duration <= 0f) return to
        val u = (now - start) / duration
        if (u <= 0f) return from
        if (u >= 1f) return to
        return from + (to - from) * easing(u)
    }

    fun done(now: Float) = duration <= 0f || now >= start + duration
}

/** Reanimated's default `withTiming` curve. */
internal val WaxEaseQuad: (Float) -> Float = { u -> if (u < 0.5f) 2f * u * u else 1f - (-2f * u + 2f).pow(2) / 2f }
private val EaseInOutBezier = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
internal val WaxEaseInOutCurve: (Float) -> Float = { EaseInOutBezier.transform(it) }
internal val WaxLinear: (Float) -> Float = { it }
