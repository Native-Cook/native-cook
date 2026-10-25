package com.ngocdevv.nativecook.blocks.onboarding.lighthousebeamwelcome

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Lighthouse Beam Welcome — timing and the beam's geometry (a port of lighthouseMotion.ts). Every
 frame of the loop is a pure function of the loop time t, so t = LOOP draws the same frame as t = 0.

 The lantern turns about a vertical axis: azimuth φ = 0 points right (across the screen), φ = π/2
 towards the viewer, π left, 3π/2 away. Seen from a little above, the beam's direction on the
 screen is (cos φ, E·sin φ + TILT): longest pointing sideways, short and wide (a flare) pointing
 at the viewer, short and faint pointing away.

 autoPlay timeline (LOOP = 10 s):
   0.0 – 1.8   rest: the beam lies across page 1's text (φ = 0), fog drifting, sea moving.
   1.8 – 10.0  two whole turns (φ 0 → 4π): speeds up over 0.7 s, turns at a constant rate
               (linear: a real rotation), slows over 0.7 s into the rest. Text is revealed only
               where the cone passes and glows on for 0.8 s after it.
   3.73 – 4.33 page 1 → 2 (ease-in-out cubic), centred on φ = π, while the beam is behind the tower.
   7.48 – 8.08 page 2 → 1, centred on φ = 3π; the last sweep reveals page 1 and rests on it.
 */

/** The autoPlay loop in seconds (the manifest's recording.loop). */
const val LOOP = 10f
private const val PIF = PI.toFloat()
/** Viewer elevation (how much "towards the viewer" drops on screen) and the beam's downward tilt. */
internal const val LIGHT_E = 0.3f
internal const val LIGHT_TILT = 0.12f
private const val TURN_START = 1.8f
private const val TURN_END = 10f
private const val RAMP = 0.7f
private const val TURNS = 2
/** Times (s) the trail looks back, for the 0.8 s afterglow. */
internal val LIGHT_TRAIL = floatArrayOf(0.1f, 0.22f, 0.36f, 0.55f, 0.8f)
internal const val LIGHT_PAGES = 3
private const val SLIDE = 0.6f

internal val LightEaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

private fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
private fun easeInOutCubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

/** 0 … 1 over [a, b] with a constant speed in the middle and smooth ramps of `ramp` s at both ends. */
private fun trapezoid(t: Float, a: Float, b: Float, ramp: Float): Float {
    val span = b - a
    val vmax = 1 / (span - ramp)
    val x = t - a
    if (x <= 0) return 0f
    if (x >= span) return 1f
    if (x < ramp) {
        val u = x / ramp
        return vmax * ramp * (u * u * u - (u * u * u * u) / 2)
    }
    if (x < span - ramp) return vmax * (ramp * 0.5f + (x - ramp))
    val u = (span - x) / ramp
    return 1 - vmax * ramp * (u * u * u - (u * u * u * u) / 2)
}

/** The lantern's azimuth at loop time t, unwrapped (0 … 4π). */
internal fun lightPhiAt(t: Float) = 2 * PIF * TURNS * trapezoid(t, TURN_START, TURN_END, RAMP)

/** φ at t − back, continuous across the loop's seam (one loop earlier is 4π less). */
internal fun lightPhiBefore(t: Float, back: Float): Float {
    val s = t - back
    return if (s >= 0) lightPhiAt(s) else lightPhiAt(s + LOOP) - 2 * PIF * TURNS
}

// The page changes sit where the beam points away from the text (φ = π and 3π), found once.
private fun timeOfPhi(target: Float): Float {
    var lo = TURN_START
    var hi = TURN_END
    repeat(40) {
        val mid = (lo + hi) / 2
        if (lightPhiAt(mid) < target) lo = mid else hi = mid
    }
    return (lo + hi) / 2
}

private val PAGE_OUT = timeOfPhi(PIF)
private val PAGE_BACK = timeOfPhi(3 * PIF)

/** The page shown, as a float (0 = page 1, 1 = page 2). */
internal fun lightPageAt(t: Float): Float {
    val out = easeInOutCubic(seg(t, PAGE_OUT - SLIDE / 2, PAGE_OUT + SLIDE / 2))
    val back = easeInOutCubic(seg(t, PAGE_BACK - SLIDE / 2, PAGE_BACK + SLIDE / 2))
    return out - back
}

/** The lantern azimuth that points the beam at a screen point (relative to the lamp), nearest `near`. */
internal fun lightAimAt(dx: Float, dy: Float, near: Float): Float {
    // Solve (dx, dy)·m = (cos φ, E sin φ + TILT) for m > 0, then read φ.
    val e2 = LIGHT_E * LIGHT_E
    val a = dx * dx + (dy * dy) / e2
    val b = (-2 * dy * LIGHT_TILT) / e2
    val c = (LIGHT_TILT * LIGHT_TILT) / e2 - 1
    val disc = max(0f, b * b - 4 * a * c)
    val m = if (a > 1e-6f) (-b + sqrt(disc)) / (2 * a) else 0f
    val cosP = dx * m
    val sinP = max(-1f, min(1f, (dy * m - LIGHT_TILT) / LIGHT_E))
    val phi = atan2(sinP, cosP)
    val turns = ((near - phi) / (2 * PIF)).roundToInt()
    return phi + turns * 2 * PIF
}

/** How much the beam faces the viewer (0 … 1): drives the lamp's flare. */
internal fun lightFacing(phi: Float): Float {
    val s = sin(phi)
    return if (s > 0) s.pow(4) else 0f
}

/** Layout in dp. The text block's content bitmap covers [contentY, contentY + contentH]. */
internal class LightGeo(val w: Float, val h: Float, top: Float, bottom: Float) {
    val u = min(w / 393f, (h - top - bottom) / 759f)
    val horizon = (top + (h - top - bottom) * 0.6f).roundToInt().toFloat()
    val lampX = (w * 0.16f).roundToInt().toFloat()
    val lampY = (top + (h - top - bottom) * 0.27f).roundToInt().toFloat()
    val galleryY = lampY + 16 * u
    val baseY = horizon + 30 * u
    val textX = max(lampX + 96 * u, w * 0.4f).roundToInt().toFloat()
    val textW = w - textX - 24
    // The text block's middle sits on the beam's axis at rest, a little right of its left edge.
    private val mid = lampY + LIGHT_TILT * (textX + textW * 0.3f - lampX)
    val textY = (mid - 80 * u).roundToInt().toFloat()
    val contentY = textY - 40
    val contentH = (180 * u).roundToInt() + 80f
    val dotsY = h - bottom - 16 - 56 - 30
}

/** A tweened value stepped by the frame loop (Reanimated's withTiming with the block's ease-in-out). */
internal class LightTween(var value: Float) {
    private var from = 0f
    private var to = 0f
    private var t0 = 0.0
    private var dur = 0f
    var active = false
        private set

    fun set(x: Float) {
        value = x
        active = false
    }

    fun tween(n: Double, target: Float, duration: Float) {
        from = value
        to = target
        t0 = n
        dur = duration
        active = true
    }

    fun step(n: Double) {
        if (!active) return
        val x = if (dur <= 0f) 1f else ((n - t0) / dur).toFloat().coerceIn(0f, 1f)
        value = from + (to - from) * LightEaseInOut.transform(x)
        if (x >= 1f) active = false
    }
}
