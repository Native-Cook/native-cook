package com.ngocdevv.nativecook.blocks.buttons.foilstampbutton

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 The Foil Stamp Button's clock (the React Native foilStampMotion.ts): every value on screen is a
 closed-form function of the loop time t (seconds), so the frame at t = LOOP is the frame at t = 0.
 Lengths are dp, angles radians.

   0.0 – 1.5   rest: blank cotton card, "Seal order" on the brass stamp.
   1.5 – 1.62  anticipation: the stamp lifts 4 dp (ease-out).
   1.62 – 2.3  it descends (ease-in-out): scale 1 → 0.94, shadows tighten.
   2.3         contact: a 0.15 s squash to 0.925, the paper dips round the seal, the blind
               emboss rises (ease-out, 0.3 s) and 16 foil flakes spring out to the seal's edge.
   2.9 – 3.6   lift: the stamp springs back (ζ 0.45, a little past rest).
   3.6 – 5.6   the foil is laid on by a diagonal sweep with a gleam at its front.
   4.2 – 4.8   the label turns into a drawn check and "Sealed".
   6.6 – 7.6   the foil and the emboss dissolve through a noise threshold; the label returns.
   7.6 – 8.0   rest (blank, as at 0).
 */

const val LOOP = 8f

private const val TAU = PI.toFloat() * 2f

internal const val FOIL_ANTICIPATION = 4f
internal const val FOIL_DESCENT = 3f
internal const val FOIL_PRESS_SCALE = 0.06f
internal const val FOIL_SQUASH = 0.015f

// The light: azimuth (radians, 0 = from the right, −π/2 = from the top) and elevation.
internal const val FOIL_LIGHT_AZ = -2.25f
private const val LIGHT_SWING = 0.5f
internal const val FOIL_LIGHT_EL = 0.62f

internal fun foilClamp01(x: Float) = min(1f, max(0f, x))

/** 0 before a, 1 after b, linear in between. */
internal fun foilSeg(t: Float, a: Float, b: Float) = foilClamp01((t - a) / (b - a))

internal fun foilEaseOut(x: Float): Float {
    val u = 1f - foilClamp01(x)
    return 1f - u * u * u
}

internal fun foilEaseInOut(x: Float): Float {
    val u = foilClamp01(x)
    return if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
}

/** A damped spring released from 1 toward 0 at time u (s): ω rad/s, ζ damping ratio. */
internal fun foilSpringFrom1(u: Float, omega: Float, zeta: Float): Float {
    if (u <= 0f) return 1f
    val wd = omega * sqrt(1f - zeta * zeta)
    val a = zeta * omega
    return exp(-a * u) * (cos(wd * u) + (a / wd) * sin(wd * u))
}

internal fun foilHash(i: Int): Float {
    val s = sin(i * 12.9898 + 4.1414) * 43758.5453
    return (s - floor(s)).toFloat()
}

/** Resistance past an edge: follows the finger at first, never further than `limit`. */
internal fun foilRubber(x: Float, limit: Float): Float {
    val s = if (x < 0f) -1f else 1f
    val a = abs(x)
    return s * limit * (1f - 1f / (1f + a / limit))
}

/** Everything the screen draws, for one frame. */
internal class FoilPose {
    var lift = 0f
    var press = 0f
    var squash = 0f
    var depth = 0f
    var foil = 0f
    var dissolve = 0f
    var ring = 0f
    var light = FOIL_LIGHT_AZ
    var check = 0f
    var flakeAge = -1f
}

internal fun FoilPose.setLoop(t: Float) {
    val up = foilEaseOut(foilSeg(t, 1.5f, 1.62f))
    val down = foilEaseInOut(foilSeg(t, 1.62f, 2.3f))
    var p = down
    if (t >= 2.9f) p = foilSpringFrom1(t - 2.9f, 11f, 0.45f)
    press = p
    lift = -FOIL_ANTICIPATION * up * (1f - down) + FOIL_DESCENT * p
    val s = foilSeg(t, 2.3f, 2.45f)
    squash = if (t >= 2.3f) sin(PI.toFloat() * s) else 0f
    ring = if (t >= 2.3f) sin(PI.toFloat() * foilSeg(t, 2.3f, 2.75f)) else 0f
    dissolve = foilEaseInOut(foilSeg(t, 6.6f, 7.6f))
    depth = if (t < 2.3f) 0f else foilEaseOut(foilSeg(t, 2.3f, 2.6f))
    foil = foilEaseInOut(foilSeg(t, 3.6f, 5.6f))
    check = foilEaseOut(foilSeg(t, 4.2f, 4.8f)) * (1f - foilEaseInOut(foilSeg(t, 6.6f, 7.0f)))
    light = FOIL_LIGHT_AZ + LIGHT_SWING * sin(TAU * t / LOOP)
    flakeAge = if (t >= 2.3f) t - 2.3f else -1f
}

// Flakes of foil that spring out from under the stamp and settle round the seal.
internal const val FOIL_FLAKES = 16

/** Flake i at `age` s after contact, relative to the seal centre, written into `out` (x, y, angle, size). */
internal fun foilFlake(i: Int, age: Float, r0: Float, out: FloatArray) {
    val a = foilHash(i) * TAU
    val outR = 1.02f + foilHash(i + 31) * 0.24f
    val fly = foilEaseOut(age / 0.55f)
    // They overshoot a little past where they settle, as they skid on the paper.
    val settle = 1f + 0.08f * sin(PI.toFloat() * foilClamp01(age / 0.55f)) * (1f - foilClamp01(age / 0.9f))
    val r = r0 * (0.7f + (outR - 0.7f) * fly) * settle
    out[0] = cos(a) * r
    out[1] = sin(a) * r
    out[2] = (foilHash(i + 7) - 0.5f) * 3f * fly + foilHash(i + 3) * TAU
    out[3] = 1.8f + foilHash(i + 11) * 2.6f
}

/*
 Small time-based animations for the finger's pose: each value is a function of the screen clock,
 so nothing is stepped per frame and a frame is a pure function of `now`.
 */
internal class FoilTrack(initial: Float) {
    private var from = initial
    private var to = initial
    private var start = 0f
    private var duration = 0f
    private var easing: (Float) -> Float = { it }
    // Spring mode: ω, ζ and the starting velocity (units/s).
    private var spring = false
    private var omega = 0f
    private var zeta = 0f
    private var v0 = 0f

    fun set(v: Float) {
        from = v; to = v; duration = 0f; spring = false
    }

    fun timing(now: Float, target: Float, seconds: Float, delay: Float = 0f, ease: (Float) -> Float = FoilEaseInOutQuad) {
        from = value(now); to = target; start = now + delay; duration = seconds; easing = ease; spring = false
    }

    /** A spring like Reanimated's duration-based one: ω = 2π / duration. */
    fun spring(now: Float, target: Float, seconds: Float, dampingRatio: Float, velocity: Float = 0f) {
        from = value(now); to = target; start = now; spring = true
        omega = 2f * PI.toFloat() / seconds; zeta = dampingRatio; v0 = velocity
        duration = seconds * 2.5f
    }

    fun value(now: Float): Float {
        if (spring) {
            val u = now - start
            if (u <= 0f) return from
            val a = from - to
            val wd = omega * sqrt(1f - zeta * zeta)
            val b = (v0 + zeta * omega * a) / wd
            return to + exp(-zeta * omega * u) * (a * cos(wd * u) + b * sin(wd * u))
        }
        if (duration <= 0f) return to
        val u = (now - start) / duration
        if (u <= 0f) return from
        if (u >= 1f) return to
        return from + (to - from) * easing(u)
    }

    /** True once the value has settled (a spring after 2.5 of its durations). */
    fun done(now: Float) = duration <= 0f || now >= start + duration
}

/** Reanimated's default `withTiming` curve. */
internal val FoilEaseInOutQuad: (Float) -> Float = { u -> if (u < 0.5f) 2f * u * u else 1f - (-2f * u + 2f).pow(2) / 2f }

private fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float): (Float) -> Float {
    val easing = androidx.compose.animation.core.CubicBezierEasing(x1, y1, x2, y2)
    return { easing.transform(it) }
}

internal val FoilEaseOutCurve = cubicBezier(0.23f, 1f, 0.32f, 1f)
internal val FoilEaseInOutCurve = cubicBezier(0.77f, 0f, 0.175f, 1f)
