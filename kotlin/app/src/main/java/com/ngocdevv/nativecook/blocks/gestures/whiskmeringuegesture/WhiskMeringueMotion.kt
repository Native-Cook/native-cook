package com.ngocdevv.nativecook.blocks.gestures.whiskmeringuegesture

import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Whisk Meringue Gesture — the loop's script, the bowl's geometry and the whisk's wires, as pure
 functions of the loop time (frame at t = LOOP equals frame at 0), ported from whiskMeringueMotion.ts.

 A pose is [foam, peaks, swirl, agitation, headX, headY, lift, liftPeak, liftX, liftY].

 autoPlay script (LOOP = 9 s):
   0.0 – 1.2   rest: clear whites, the whisk resting against the right of the bowl.
   1.2 – 5.6   circles: the whisk speeds up to 2.3 rev/s, settles to 1.7 rev/s, then slows to a
               stop (5.4 – 6.2); the whites go foamy, soft, then firm, the contents swirl.
   5.6 – 6.4   peaks: the foam turns glossy and stiff.
   6.4 – 7.2   the whisk lifts and pulls a stiff peak up with it.
   7.2 – 8.4   reset: the whisk settles back while the bubbles pop back into clear whites.
   8.4 – 9.0   rest.
 */

internal const val LOOP = 9.0
internal const val F_FOAM = 0
internal const val F_PEAK = 1
internal const val F_SWIRL = 2
internal const val F_AGIT = 3
internal const val F_HX = 4
internal const val F_HY = 5
internal const val F_LIFT = 6
internal const val F_LPEAK = 7
internal const val F_LX = 8
internal const val F_LY = 9

internal const val WhiskSquash = 0.85
private const val CIRCLE_R = 0.44
private const val REST_X = 0.5
private const val REST_Y = 0.14
private const val GRIP_X = 1.32
private const val GRIP_Y = 1.12

private fun clamp01(x: Double) = min(1.0, max(0.0, x))
private fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
private fun easeOut(x: Double): Double {
    val u = 1 - x
    return 1 - u * u * u
}
internal fun whiskEaseInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

internal fun whiskMix(a: DoubleArray, b: DoubleArray, k: Double) = DoubleArray(a.size) { a[it] + (b[it] - a[it]) * k }

internal fun whiskRestPose() = doubleArrayOf(0.0, 0.0, 0.0, 0.0, REST_X, REST_Y, 0.0, 0.0, 0.0, 0.0)

/** The meringue held still (reduced motion): stiff, glossy, a peak standing. */
internal fun whiskStiffPose() = doubleArrayOf(1.0, 1.0, 2.2, 0.0, REST_X, REST_Y, 0.0, 1.0, -0.12, -0.05)

private fun steadyTurns(x: Double) = 0.92 + 1.7 * x + 0.6 * 0.5 * (1 - exp(-x / 0.5))

private fun turnsAt(t: Double): Double {
    if (t < 1.2) return 0.0
    if (t < 2.0) {
        val u = (t - 1.2) / 0.8
        return 0.92 * u * u
    }
    if (t < 5.4) return steadyTurns(t - 2.0)
    val s0 = 1.7 + 0.6 * exp(-3.4 / 0.5)
    val base = steadyTurns(3.4)
    val x = min(t, 6.2) - 5.4
    return base + s0 * x - s0 * x * x / (2 * 0.8)
}

private fun whiskSpeedAt(t: Double): Double {
    if (t < 1.2 || t >= 6.2) return 0.0
    if (t < 2.0) return 2 * 0.92 * (t - 1.2) / 0.64
    if (t < 5.4) return 1.7 + 0.6 * exp(-(t - 2.0) / 0.5)
    return (1.7 + 0.6 * exp(-3.4 / 0.5)) * (1 - (t - 5.4) / 0.8)
}

internal fun whiskPoseAt(t: Double): DoubleArray {
    val p = whiskRestPose()
    val ang = turnsAt(t) * PI * 2
    val reset = whiskEaseInOut(seg(t, 7.3, 8.4))
    val u = seg(t, 1.4, 5.6)
    val foam = 0.86 * u * (2 - u) + 0.14 * whiskEaseInOut(seg(t, 5.6, 6.4))
    p[F_FOAM] = foam * (1 - reset)
    p[F_PEAK] = (0.35 * whiskEaseInOut(seg(t, 3.4, 5.6)) + 0.65 * whiskEaseInOut(seg(t, 5.6, 6.4))) * (1 - reset)
    p[F_SWIRL] = if (t < 8.4) 0.62 * ang else 0.0
    p[F_AGIT] = min(1.0, whiskSpeedAt(t) / 2.3)
    val cx = CIRCLE_R * cos(ang)
    val cy = CIRCLE_R * sin(ang)
    val enter = whiskEaseInOut(seg(t, 1.2, 1.55))
    var hx = REST_X + (cx - REST_X) * enter
    var hy = REST_Y + (cy - REST_Y) * enter
    val endAng = turnsAt(6.2) * PI * 2
    val ex = CIRCLE_R * cos(endAng)
    val ey = CIRCLE_R * sin(endAng)
    val lift = easeOut(seg(t, 6.4, 7.2))
    val back = whiskEaseInOut(seg(t, 7.2, 8.4))
    if (t >= 6.2) {
        hx = ex + (REST_X - ex) * back
        hy = ey - 0.08 * lift + (REST_Y - ey + 0.08) * back
    }
    p[F_HX] = hx
    p[F_HY] = hy
    p[F_LIFT] = lift * (1 - back)
    p[F_LPEAK] = easeOut(seg(t, 6.45, 7.05)) * (1 - reset)
    p[F_LX] = ex
    p[F_LY] = ey
    return p
}

internal val WhiskStages = listOf("Clear whites", "Foamy", "Soft peaks", "Firm peaks", "Stiff peaks")

internal fun whiskStageOf(foam: Double) = when {
    foam < 0.06 -> 0
    foam < 0.36 -> 1
    foam < 0.66 -> 2
    foam < 0.92 -> 3
    else -> 4
}

/** The screen's geometry (dp). */
internal class WhiskGeo(val w: Double, val h: Double, top: Double, bottom: Double) {
    val unit = max(0.74, min(1.15, min(w / 393, h / 852)))
    val r = min(w * 0.43, 172 * unit)
    val cx = w / 2
    val cy = top + (bottom - top) * 0.47
}

/**
 * The whisk seen from above: six wire loops sharing the tip and gathering into the ferrule, then
 * the handle up to the hand. `lift` brings it towards the camera (bigger). Written into the paths.
 */
internal fun whiskPaths(g: WhiskGeo, hx: Double, hy: Double, lift: Double, dx: Double, dy: Double, wires: Path, handle: Path) {
    val x0 = g.cx + hx * g.r
    val y0 = g.cy + hy * g.r * WhiskSquash
    val gx = g.cx + GRIP_X * g.r
    val gy = g.cy + GRIP_Y * g.r * WhiskSquash
    var ax = gx - x0
    var ay = gy - y0
    val len = max(1.0, hypot(ax, ay))
    ax /= len
    ay /= len
    val s = g.unit * (1 + 0.2 * lift)
    val l = 124 * s
    val wd = 40 * s
    val tx = x0 - ax * l * 0.32 + dx
    val ty = y0 - ay * l * 0.32 + dy
    val nx = -ay
    val ny = ax
    wires.reset()
    for (i in 0 until 6) {
        val phi = i / 6.0 * PI + 0.26
        val lateral = wd * cos(phi)
        for (k in 0..24) {
            val sArc = k / 24.0 * PI * 2
            val along = l * (1 - cos(sArc / 2)) / 2
            val swell = sin(sArc / 2) * (1 - along / l).coerceAtLeast(0.0).pow(0.35)
            val side = if (sin(sArc) >= 0) 1.0 else -1.0
            val off = lateral * swell * side
            val px = (tx + ax * along + nx * off).toFloat()
            val py = (ty + ay * along + ny * off).toFloat()
            if (k == 0) wires.moveTo(px, py) else wires.lineTo(px, py)
        }
    }
    val fx = tx + ax * l
    val fy = ty + ay * l
    handle.reset()
    handle.moveTo(fx.toFloat(), fy.toFloat())
    handle.lineTo((fx + ax * 190 * s).toFloat(), (fy + ay * 190 * s).toFloat())
}

/** A finger in bowl units, kept inside the bowl with a rubber band past 0.8 of the radius. */
internal fun whiskFingerInBowl(g: WhiskGeo, x: Double, y: Double, out: DoubleArray) {
    val bx = (x - g.cx) / g.r
    val by = (y - g.cy) / (g.r * WhiskSquash)
    val r = hypot(bx, by)
    val lim = 0.8
    if (r <= lim) {
        out[0] = bx
        out[1] = by
        return
    }
    val over = r - lim
    val rr = lim + over / (1 + over / 0.12)
    out[0] = bx / r * rr
    out[1] = by / r * rr
}
