package com.ngocdevv.nativecook.blocks.text.windscatterletters

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Wind Scatter Letters — where every glyph and leaf is, as a pure function of the loop time, and the
 small simulation a finger drives (a port of windScatterMotion.ts).

 LOOP = 9 s
   0.0 – 1.6   rest: the headline set, six leaves lying on the ridge.
   1.6 – 2.4   the gust builds: wind streaks sweep in, the glyphs tremble (up to 3°), eight
               leaves blow in from the left edge.
   2.2 – 3.2   the glyphs lift off one by one from the left (0.45 s across the line), each easing
               out toward its own drift while circling an eddy, tumbling and swelling or
               shrinking with depth.
   5.0 – 7.2   they come home on an underdamped spring (ζ 0.55, ω 9 rad/s), 30 ms apart.
   7.2 – 9.0   rest.

 Bodies are the headline's glyphs followed by the leaves on the ridge; the eight leaves that cross
 the screen are separate and always analytic.
*/

const val LOOP = 9f
internal const val WIND_GROUND_LEAVES = 6
internal const val WIND_FLYING_LEAVES = 8
private const val LIFT_AT = 2.2f
private const val LIFT_SPREAD = 0.45f
private const val LIFT_LEN = 1.0f
private const val RETURN_AT = 5.0f
private const val RETURN_STAGGER = 0.03f
private const val ZETA = 0.55f
private const val OMEGA = 9f
private const val SETTLED = 2.1f

internal fun windClamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
internal fun windSeg(t: Float, a: Float, b: Float) = windClamp01((t - a) / (b - a))
internal fun windEaseOutCubic(x: Float): Float { val u = 1 - x; return 1 - u * u * u }
internal fun windEaseInOutCubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

internal fun windHash(i: Float): Float {
    val s = sin(i * 12.9898 + 78.233) * 43758.5453
    return (s - floor(s)).toFloat()
}

/** Unit step response of the return spring. */
private fun springStep(tau: Float): Float {
    if (tau <= 0f) return 0f
    if (tau >= SETTLED) return 1f
    val wd = OMEGA * sqrt(1 - ZETA * ZETA)
    val e = exp(-ZETA * OMEGA * tau)
    return 1 - e * (cos(wd * tau) + (ZETA / sqrt(1 - ZETA * ZETA)) * sin(wd * tau))
}

/** How strong the gust is (0…1): streaks, tremble, trails. */
internal fun windGustAt(t: Float) = windEaseInOutCubic(windSeg(t, 1.6f, 2.4f)) * (1 - windEaseInOutCubic(windSeg(t, 4.6f, 5.6f)))

/** Depth of body i: every third glyph sits behind the others (shrinks, blurs), the rest come forward. */
internal fun windDepthOf(i: Int): Float {
    val h = windHash(i * 9.13f + 4f)
    return if (i % 3 == 1) -(0.15f + 0.15f * h) else 0.1f + 0.35f * h
}

/** A body's home: its centre, and how far it may scatter (1 for glyphs, less for leaves). */
internal class WindHome(val x: Float, val y: Float, val reach: Float)

/** [dx, dy, rotation (rad), scale] of body i at loop time t, relative to its home, into `out` at `o`. */
internal fun windAutoOffset(t: Float, i: Int, home: WindHome, W: Float, H: Float, out: FloatArray, o: Int) {
    val h1 = windHash(i * 3.17f + 1f)
    val h2 = windHash(i * 5.31f + 2f)
    val h3 = windHash(i * 7.77f + 3f)
    val h4 = windHash(i * 9.13f + 4f)
    val lift = LIFT_AT + LIFT_SPREAD * windClamp01(home.x / W) + 0.05f * h1
    val tremble = (3 * PI.toFloat()) / 180 * windEaseInOutCubic(windSeg(t, 1.6f, lift)) * sin(t * 23 + i * 1.9f)
    if (t < lift) { out[o] = 0f; out[o + 1] = 0f; out[o + 2] = tremble; out[o + 3] = 1f; return }

    val tx = min(W - 40, max(40f, home.x + (60 + 240 * h1) * home.reach))
    val ty = min(H * 0.82f, max(H * 0.12f, home.y + (-250 + 440 * h2) * home.reach))
    val ax = tx - home.x
    val ay = ty - home.y
    val r = (20 + 40 * h3) * home.reach
    val w = (1.5f + 1.5f * h4) * (if (h1 > 0.5f) 1 else -1)
    val spin = (0.8f + 1.4f * h3) * (if (h2 > 0.5f) 1 else -1)
    val depth = windDepthOf(i)

    val ret = RETURN_AT + RETURN_STAGGER * i
    val tf = min(t, ret) - lift
    val e = windEaseOutCubic(windClamp01(tf / LIFT_LEN))
    val th = h1 * 6.283f + w * tf
    val sx = e * ax * (1 - exp(-tf / 0.9f)) + e * r * (cos(th) - cos(h1 * 6.283f))
    val sy = e * ay * (1 - exp(-tf / 0.9f)) + e * r * (sin(th) - sin(h1 * 6.283f))
    val rot = e * (spin * tf + 0.44f * sin(tf * 5 + i))
    val sc = 1 + e * depth * (1 + 0.1f * sin(tf * 3 + i))
    if (t < ret) { out[o] = sx; out[o + 1] = sy; out[o + 2] = rot + tremble * (1 - e); out[o + 3] = sc; return }

    val k = 1 - springStep(t - ret)
    val a = 0.6f * k
    val c = cos(a)
    val s = sin(a)
    out[o] = k * (sx * c - sy * s)
    out[o + 1] = k * (sx * s + sy * c)
    out[o + 2] = k * rot
    out[o + 3] = 1 + k * (sc - 1)
}

/** One of the leaves crossing the screen: [x, y, rotation, flip (−1…1 as scaleX), scale] into `out`. */
internal fun windFlyingLeaf(t: Float, j: Int, W: Float, H: Float, out: FloatArray) {
    val h1 = windHash(j * 11.3f + 21f)
    val h2 = windHash(j * 4.7f + 22f)
    val start = 1.75f + 1.9f * h1
    val dur = 2.2f + 1.2f * h2
    val u = windSeg(t, start, start + dur)
    if (u <= 0f || u >= 1f) { out[0] = -200f; out[1] = -200f; out[2] = 0f; out[3] = 1f; out[4] = 1f; return }
    out[0] = -50 + (W + 100) * windEaseInOutCubic(u)
    val y0 = H * (0.18f + 0.55f * h2)
    out[1] = y0 + sin(u * 6.283f * (1 + h1)) * 40 + u * 90
    out[2] = u * (5 + 6 * h1) * (if (h2 > 0.5f) 1 else -1)
    out[3] = cos(u * 6.283f * (2 + h2 * 2))
    out[4] = 0.85f + 0.5f * h1
}

/** Simulation state for touch: per body offset, velocity, rotation and its speed, plus two past positions for trails. */
internal class WindSim(n: Int) {
    val x = FloatArray(n)
    val y = FloatArray(n)
    val vx = FloatArray(n)
    val vy = FloatArray(n)
    val r = FloatArray(n)
    val vr = FloatArray(n)
    val px0 = FloatArray(n)
    val py0 = FloatArray(n)
    val px1 = FloatArray(n)
    val py1 = FloatArray(n)
}

/** Finger wind: at (fx, fy) moving at (fvx, fvy) dp/s, `on` while touching. */
internal class WindWind {
    var fx = 0f
    var fy = 0f
    var fvx = 0f
    var fvy = 0f
    var on = false
}

/** One step of the touch simulation. Home springs are soft while the wind blows, firm when settling. */
internal fun windStepSim(s: WindSim, homes: List<WindHome>, wind: WindWind, dt: Float, settle: Boolean) {
    val k = if (settle) 70f else 9f
    val c = if (settle) 2 * sqrt(70f) else 2.2f
    for (i in homes.indices) {
        s.px1[i] = s.px0[i]
        s.py1[i] = s.py0[i]
        s.px0[i] = s.x[i]
        s.py0[i] = s.y[i]
        var ax = -k * s.x[i] - c * s.vx[i]
        var ay = -k * s.y[i] - c * s.vy[i]
        var ar = -k * s.r[i] - c * s.vr[i]
        if (wind.on) {
            val dx = homes[i].x + s.x[i] - wind.fx
            val dy = homes[i].y + s.y[i] - wind.fy
            val d2 = dx * dx + dy * dy
            val near = exp(-d2 / (2 * 120f * 120f)) * homes[i].reach
            val sp = min(3000f, hypot(wind.fvx, wind.fvy))
            val gate = if (sp > 40f) 1f else 0f
            ax += (wind.fvx * (0.15f + 4.5f * near)) * gate
            ay += (wind.fvy * (0.15f + 4.5f * near) - 60 * near) * gate
            ar += ((dx * wind.fvy - dy * wind.fvx) / 4000f) * near
        }
        s.vx[i] += ax * dt
        s.vy[i] += ay * dt
        s.vr[i] += ar * dt
        s.x[i] += s.vx[i] * dt
        s.y[i] += s.vy[i] * dt
        s.r[i] += s.vr[i] * dt
    }
}

internal fun windSimEnergy(s: WindSim): Float {
    var e = 0f
    for (i in s.x.indices) e = max(e, abs(s.x[i]) + abs(s.y[i]) + abs(s.vx[i]) * 0.05f + abs(s.vy[i]) * 0.05f + abs(s.r[i]) * 30)
    return e
}
