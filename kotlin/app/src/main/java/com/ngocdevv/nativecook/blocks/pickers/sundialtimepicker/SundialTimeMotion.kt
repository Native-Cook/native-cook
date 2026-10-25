package com.ngocdevv.nativecook.blocks.pickers.sundialtimepicker

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/*
 Sundial Time Picker — the sun, the dial's geometry and the loop's script, as pure functions of
 the loop time (frame at t = LOOP equals frame at 0), ported from sundialMotion.ts.

 Time τ is in hours (7 … 17). The sun follows an equinox day at latitude 40° N: hour angle
 h = (τ − 12) · 15°, direction (east, north, up) = (−sin h, −sin φ cos h, cos φ cos h). The dial is
 a horizontal sundial, north up on screen; the shadow edge falls on the hour line atan(sin φ tan h).

 autoPlay script (LOOP = 9 s):
   0.0 – 1.4   rest at 10:00; 1.2 – 1.4 a finger lands on the sun.
   1.4 – 4.4   drag to 15:30 (ease-in-out); the readout rolls through every quarter.
   4.4         snap: 15:30 with a small overshoot that settles by 4.7.
   4.4 – 5.6   hold.
   5.6 – 7.6   back to 10:00 (ease-in-out), the finger lifts at 7.6.
   7.6 – 9.0   rest.
 */

internal const val LOOP = 9.0
internal const val SundialMin = 7.0
internal const val SundialMax = 17.0
internal const val SundialRest = 10.0
private const val LAT = 40 * PI / 180
internal val SundialSinLat = sin(LAT)
internal val SundialCosLat = cos(LAT)

internal class DialGeo(val w: Double, val h: Double, top: Double) {
    val unit = max(0.74, min(1.15, min(w / 393, h / 852)))
    val r = min(150 * unit, w * 0.38)
    val cx = w / 2
    val cy = top + 380 * unit
    val arcX = min(w / 2 - 22, r + 30 * unit)
    val arcY = r + 50 * unit
    val gnomon = r * 0.45
}

private fun clamp01(x: Double) = min(1.0, max(0.0, x))
private fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
internal fun sundialEaseInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

internal fun sundialTimeAt(t: Double): Double {
    if (t < 1.4) return SundialRest
    if (t < 4.4) return SundialRest + (15.5 - SundialRest) * sundialEaseInOut(seg(t, 1.4, 4.4))
    if (t < 5.6) {
        val u = seg(t, 4.4, 4.7)
        return 15.5 + 0.1 * sin(PI * u) * (1 - u)
    }
    if (t < 7.6) return 15.5 + (SundialRest - 15.5) * sundialEaseInOut(seg(t, 5.6, 7.6))
    return SundialRest
}

internal fun sundialFingerAt(t: Double): Double = when {
    t >= 1.2 && t < 4.65 -> seg(t, 1.2, 1.4) * (1 - seg(t, 4.45, 4.65))
    t >= 5.4 && t < 7.8 -> seg(t, 5.4, 5.6) * (1 - seg(t, 7.6, 7.8))
    else -> 0.0
}

/** The sun's direction [east, north, up] at time τ. */
internal fun sundialSunDir(tau: Double, out: DoubleArray) {
    val h = (tau - 12) * 15 * PI / 180
    out[0] = -sin(h)
    out[1] = -SundialSinLat * cos(h)
    out[2] = SundialCosLat * cos(h)
}

internal fun sundialSunX(g: DialGeo, tau: Double) = g.cx + g.arcX * cos(PI * (1 - (tau - 6) / 12))
internal fun sundialSunY(g: DialGeo, tau: Double) = g.cy - g.arcY * sin(PI * (1 - (tau - 6) / 12))

/** The time a finger at (x, y) asks for: the sun's place on the arc, or the shadow's direction on the dial. */
internal fun sundialTimeFromFinger(g: DialGeo, x: Double, y: Double, onDial: Boolean): Double {
    if (onDial) {
        val a = atan2(x - g.cx, g.cy - y)
        val clamped = max(-1.45, min(1.45, a))
        return 12 + atan(tan(clamped) / SundialSinLat) * 12 / PI
    }
    val b = atan2((g.cy - y) / g.arcY, (x - g.cx) / g.arcX)
    val bb = if (b < -PI / 2) PI else max(0.0, b)
    return 6 + 12 * (1 - bb / PI)
}

internal fun sundialRubberTime(tau: Double): Double {
    if (tau > SundialMax) return SundialMax + (tau - SundialMax) / (1 + (tau - SundialMax) / 0.4)
    if (tau < SundialMin) return SundialMin - (SundialMin - tau) / (1 + (SundialMin - tau) / 0.4)
    return tau
}

internal fun sundialQuarterOf(tau: Double) = min(SundialMax, max(SundialMin, floor(tau * 4 + 0.5) / 4))
