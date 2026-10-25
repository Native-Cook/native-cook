package com.ngocdevv.nativecook.blocks.gestures.potterywheelshape

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Pottery Wheel Shape — the loop's script and the clay's geometry, as pure functions of the loop
 time (every frame at t = LOOP equals the frame at t = 0), ported from potteryWheelShapeMotion.ts.

 The clay is a surface of revolution: a height H and eight radii r0…r7 spaced evenly from the foot
 to the top, blended with a cubic B-spline (the shader uses the same formula), plus `open`
 (0 = a closed, domed lump; 1 = a vessel with an open top) and a wobble.

 Units: dp at the 393 × 852 design size (multiplied by `unit` on other screens), seconds, rev/s.

 autoPlay script (LOOP = 9 s):
   0.0 – 1.4   rest: a squat lump, a little off centre, turning slowly (0.6 rev/s).
   1.4 – 2.4   centring: both hands press in, the wheel speeds up to 2.2 rev/s, the lump becomes a
               cone; 2.3 – 2.9 the thumb opens the top.
   2.4 – 4.4   pull up: one finger rides the wall from the foot to the lip, the walls rise.
   4.4 – 5.6   neck in: both hands squeeze the upper third, the belly swells.
   5.6 – 6.2   lip flare.
   6.2 – 7.2   hold: the finished vase spins.
   7.2 – 8.6   collapse back to the lump while the wheel slows to 0.6 rev/s.
   8.6 – 9.0   rest (the same frame as 0).
 */

internal const val LOOP = 9.0
internal const val P_H = 0
internal const val P_R = 1
internal const val P_OPEN = 9
internal const val P_WOB = 10

internal const val PotterySlow = 0.6
internal const val PotteryFast = 2.2
internal const val PotteryPtPerCm = 13.3
internal const val PotterySinE = 0.2756
internal const val PotteryCosE = 0.9613

private val LUMP = doubleArrayOf(104.0, 80.0, 82.0, 81.0, 78.0, 73.0, 66.0, 58.0, 52.0, 0.0, 3.5)
private val CONE = doubleArrayOf(130.0, 64.0, 64.0, 62.0, 58.0, 53.0, 47.0, 41.0, 37.0, 0.0, 0.0)
private val CYL = doubleArrayOf(226.0, 56.0, 55.0, 52.0, 49.0, 46.0, 44.0, 42.0, 41.0, 1.0, 0.0)
private val NECK = doubleArrayOf(236.0, 58.0, 66.0, 71.0, 64.0, 47.0, 31.0, 27.0, 30.0, 1.0, 0.0)
private val FLARE = doubleArrayOf(240.0, 58.0, 66.0, 71.0, 64.0, 47.0, 31.0, 31.0, 44.0, 1.0, 0.0)

internal fun potteryRestPose(): DoubleArray = LUMP.copyOf()
internal fun potteryVasePose(): DoubleArray = FLARE.copyOf()

private fun clamp01(x: Double) = min(1.0, max(0.0, x))
private fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
private fun easeOut(x: Double): Double {
    val u = 1 - x
    return 1 - u * u * u
}
internal fun potteryEaseInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

internal fun potteryHash(i: Int): Double {
    val s = sin(i * 12.9898 + 78.233) * 43758.5453
    return s - floor(s)
}

internal fun potteryMix(a: DoubleArray, b: DoubleArray, k: Double): DoubleArray =
    DoubleArray(a.size) { a[it] + (b[it] - a[it]) * k }

private fun bspline(d: Double): Double {
    val x = abs(d)
    if (x >= 2) return 0.0
    if (x >= 1) return (2 - x) * (2 - x) * (2 - x) / 6
    return (4 - 6 * x * x + 3 * x * x * x) / 6
}

/** The clay's radius (dp, before `unit`) at height y above the wheel head. */
internal fun potteryRadiusAt(p: DoubleArray, y: Double): Double {
    val h = p[P_H]
    val u = clamp01(y / h) * 7
    var sum = 0.0
    var wsum = 0.0
    for (i in 0 until 8) {
        val w = bspline(u - i)
        sum += w * p[P_R + i]
        wsum += w
    }
    var r = sum / max(wsum, 1e-3)
    val cap = sqrt(clamp01((h - y) / (0.42 * h)))
    r *= cap + (1 - cap) * p[P_OPEN]
    return r + 5 * exp(-max(y, 0.0) / 5)
}

/** The widest diameter of the profile (dp). */
internal fun potteryWidest(p: DoubleArray): Double {
    var m = 0.0
    for (k in 0..16) m = max(m, potteryRadiusAt(p, p[P_H] * k / 16))
    return 2 * m
}

internal fun potteryPoseAt(t: Double): DoubleArray {
    if (t < 1.4) return LUMP.copyOf()
    if (t < 2.4) return potteryMix(LUMP, CONE, potteryEaseInOut(seg(t, 1.4, 2.4)))
    if (t < 4.4) {
        val p = potteryMix(CONE, CYL, potteryEaseInOut(seg(t, 2.4, 4.4)))
        p[P_OPEN] = potteryEaseInOut(seg(t, 2.3, 2.9))
        return p
    }
    if (t < 5.6) return potteryMix(CYL, NECK, potteryEaseInOut(seg(t, 4.4, 5.6)))
    if (t < 7.2) return potteryMix(NECK, FLARE, potteryEaseInOut(seg(t, 5.6, 6.2)))
    if (t < 8.6) {
        val u = seg(t, 7.2, 8.6)
        // The walls sag past the lump and come back: an overshoot that is zero at both ends.
        val k = potteryEaseInOut(u) + 0.22 * sin(PI * 2 * u) * (1 - u) * (if (u < 0.5) 1.0 else 0.6)
        val p = potteryMix(FLARE, LUMP, k)
        p[P_OPEN] = 1 - potteryEaseInOut(seg(t, 7.3, 8.1))
        p[P_WOB] = LUMP[P_WOB] * easeOut(seg(t, 7.8, 8.6))
        return p
    }
    return LUMP.copyOf()
}

private fun rampArea(t: Double): Double {
    var a = 0.0
    val up = seg(t, 1.4, 2.4)
    a += 1.0 * (up * up * up - up * up * up * up / 2)
    a += max(0.0, min(t, 7.2) - 2.4)
    val dn = seg(t, 7.2, 8.6)
    a += 1.4 * (dn - (dn * dn * dn - dn * dn * dn * dn / 2))
    return a
}

internal fun potterySpeedAt(t: Double): Double {
    val up = seg(t, 1.4, 2.4)
    val dn = seg(t, 7.2, 8.6)
    val s = up * up * (3 - 2 * up) - dn * dn * (3 - 2 * dn)
    return PotterySlow + (PotteryFast - PotterySlow) * s
}

/** The wheel's angle (radians): exactly 15 whole turns per loop, so the seam is exact. */
internal fun potterySpinAt(t: Double): Double {
    val revs = PotterySlow * t + (PotteryFast - PotterySlow) * rampArea(t)
    return revs * PI * 2
}

/** [height, side, alpha, press] for each of two fingers. */
internal fun potteryFingersAt(t: Double, p: DoubleArray): DoubleArray {
    val h = p[P_H]
    val out = doubleArrayOf(0.0, 1.0, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0)
    if (t >= 1.3 && t < 2.55) {
        val a = seg(t, 1.3, 1.5) * (1 - seg(t, 2.35, 2.55))
        val press = 4 + 3 * sin(seg(t, 1.4, 2.4) * PI)
        out[0] = h * 0.42; out[2] = a; out[3] = press
        out[4] = h * 0.42; out[6] = a; out[7] = press
    } else if (t >= 2.45 && t < 4.6) {
        out[0] = h * (0.08 + 0.86 * potteryEaseInOut(seg(t, 2.5, 4.4)))
        out[2] = seg(t, 2.45, 2.65) * (1 - seg(t, 4.4, 4.6))
        out[3] = 5.0
    } else if (t >= 4.4 && t < 5.75) {
        val a = seg(t, 4.4, 4.6) * (1 - seg(t, 5.5, 5.75))
        out[0] = h * 0.74; out[2] = a; out[3] = 6.0
        out[4] = h * 0.74; out[6] = a; out[7] = 6.0
    } else if (t >= 5.5 && t < 6.45) {
        out[0] = h - 10
        out[2] = seg(t, 5.5, 5.7) * (1 - seg(t, 6.2, 6.45))
        out[3] = 30.0
    }
    return out
}

/** How hard slip flies off the wall (0 … 1) and from which height (share of H). */
internal fun potterySprayAt(t: Double, out: DoubleArray) {
    when {
        t >= 1.5 && t < 2.4 -> { out[0] = 0.35; out[1] = 0.42 }
        t >= 2.6 && t < 4.4 -> { out[0] = 1.0; out[1] = 0.08 + 0.86 * potteryEaseInOut(seg(t, 2.5, 4.4)) }
        t >= 4.6 && t < 5.5 -> { out[0] = 0.55; out[1] = 0.74 }
        t >= 5.7 && t < 6.2 -> { out[0] = 0.5; out[1] = 0.97 }
        else -> { out[0] = 0.0; out[1] = 0.5 }
    }
}

internal val PotteryPhases = listOf(
    "Wedged and ready", "Centring the clay", "Pulling the walls up", "Collaring the neck",
    "Flaring the lip", "Looking good", "Back to a ball", "Shaping by hand",
)

internal fun potteryPhaseAt(t: Double): Int = when {
    t < 1.4 -> 0
    t < 2.4 -> 1
    t < 4.4 -> 2
    t < 5.6 -> 3
    t < 6.2 -> 4
    t < 7.2 -> 5
    t < 8.6 -> 6
    else -> 0
}

internal const val PotterySprayN = 38
internal const val PotterySprayPeriod = 0.6

/** The screen's geometry (dp): the wheel head's centre and the scale of the hero. */
internal class PotteryGeo(val w: Double, val h: Double) {
    val unit = max(0.74, min(1.15, min(w / 393, h / 852)))
    val cx = w / 2
    val baseY = h * 0.69
}

/** A finger from potteryFingersAt (at offset o) → screen [x, y, alpha, radius], written into out at oo. */
internal fun potteryFingerOnScreen(g: PotteryGeo, p: DoubleArray, f: DoubleArray, o: Int, wob: Double, out: DoubleArray, oo: Int) {
    val r = potteryRadiusAt(p, f[o]) * g.unit
    val pad = 15 * g.unit
    out[oo] = g.cx + wob + f[o + 1] * (r + pad - f[o + 3] * g.unit)
    out[oo + 1] = g.baseY - f[o] * g.unit * PotteryCosE
    out[oo + 2] = f[o + 2]
    out[oo + 3] = pad
}

internal fun potteryWobbleX(p: DoubleArray, spin: Double, unit: Double) = p[P_WOB] * unit * sin(spin + 0.6)

private const val R_MIN = 16.0
private const val R_MAX = 104.0
private const val H_MIN = 70.0
private const val H_MAX = 268.0

private fun rubber(v: Double, lo: Double, hi: Double): Double {
    if (v > hi) return hi + (v - hi) / (1 + (v - hi) / 30)
    if (v < lo) return lo - (lo - v) / (1 + (lo - v) / 30)
    return v
}

/**
 * A finger working the wall: a drag of (dx, dy) dp at screen (x, y). Pressing towards the axis
 * squeezes the radii near that height and pushes the clay up; dragging up pulls it taller.
 */
internal fun potteryShapeByFinger(g: PotteryGeo, p: DoubleArray, x: Double, y: Double, dx: Double, dy: Double): DoubleArray {
    val out = p.copyOf()
    val u = g.unit
    val h0 = p[P_H]
    val h = min(h0, max(0.0, (g.baseY - y) / (u * PotteryCosE)))
    val side = if (x >= g.cx) 1.0 else -1.0
    val reach = potteryRadiusAt(p, h) * u
    val nearWall = abs(abs(x - g.cx) - reach) < 70 * u
    if (!nearWall) return out
    val inward = -side * dx / u
    val k = h / h0 * 7
    var squeezed = 0.0
    for (i in 0 until 8) {
        val w = exp(-((i - k) * (i - k)) / (2 * 1.25 * 1.25))
        val r = out[P_R + i] - inward * w * 0.85
        out[P_R + i] = rubber(r, R_MIN, R_MAX)
        squeezed += inward * w
    }
    val up = -dy / u
    val hn = rubber(h0 + up * 0.9 + squeezed * 0.35, H_MIN, H_MAX)
    val keep = sqrt(h0 / hn)
    for (i in 0 until 8) out[P_R + i] = rubber(out[P_R + i] * keep, R_MIN, R_MAX)
    out[P_H] = hn
    out[P_OPEN] = min(1.0, max(out[P_OPEN], (hn - 120) / 40))
    out[P_WOB] = out[P_WOB] * 0.96
    return out
}

/** Brings a hand-made pose back inside the limits (after the finger lifts). */
internal fun potterySettlePose(p: DoubleArray, k: Double): DoubleArray {
    val out = p.copyOf()
    out[P_H] += (min(H_MAX, max(H_MIN, p[P_H])) - p[P_H]) * k
    for (i in 0 until 8) {
        val r = p[P_R + i]
        out[P_R + i] += (min(R_MAX, max(R_MIN, r)) - r) * k
    }
    return out
}

internal fun potterySpeedOf(vx: Double, vy: Double) = min(1.0, hypot(vx, vy) / 900)
