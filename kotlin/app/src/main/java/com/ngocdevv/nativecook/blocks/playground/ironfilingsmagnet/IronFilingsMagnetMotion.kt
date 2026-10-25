package com.ngocdevv.nativecook.blocks.playground.ironfilingsmagnet

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Iron Filings Magnet — layout, the magnets' script and the field (a port of ironMotion.ts).

 Each bar magnet is two poles (N +1 at the right end in its own frame, S −1 at the left), and the
 field at a point is the sum over poles of q·r / (|r|² + ε²)^1.5. A filing lines up with the field
 (a needle: θ and θ + π are the same), more firmly where the field is strong, and is pulled a few
 dp towards the nearest pole, so filings crowd at the ends.

 autoPlay timeline (LOOP = 9 s):
   0.0 – 1.4   rest: magnet A in the middle under the paper, filings along its dipole lines.
   1.4 – 5.4   A traces a figure eight (x = sin 2πs, y = sin 4πs, s ease-in-out sine) and turns
               ±17° with it; each filing follows 30 – 520 ms late, later the farther it is.
   5.4 – 6.4   magnet B slides in from the right edge, north end first (ease-out cubic); A is
               pushed 40 dp left with a small overshoot (5.55 – 6.6).
   6.4 – 7.2   hold: the repulsion pattern.
   7.2 – 8.2   B slides back out (ease-in-out cubic); A returns to the middle (7.3 – 8.2).
   8.2 – 9.0   rest.
 */

/** The autoPlay loop in seconds (the manifest's recording.loop). */
const val LOOP = 9f
internal const val IRON_FILINGS = 900
private const val PIF = PI.toFloat()

private fun ironClamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
private fun ironSeg(t: Float, a: Float, b: Float) = ironClamp01((t - a) / (b - a))
private fun easeOutCubic(x: Float): Float { val u = 1 - x; return 1 - u * u * u }
private fun easeInOutCubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun easeInOutSine(x: Float) = 0.5f - 0.5f * cos(PIF * x)
private fun easeOutBack(x: Float): Float {
    val c1 = 1.2f
    val c3 = c1 + 1
    val u = x - 1
    return 1 + c3 * u * u * u + c1 * u * u
}

/** Layout in dp for a screen of w × h with system bars of top and bottom dp. */
internal class IronGeo(val w: Float, val h: Float, top: Float, bottom: Float) {
    val u = min(w / 393f, (h - top - bottom) / 759f)
    val paperX = 12f
    val paperY = top + 4f
    val paperW = w - 24f
    val paperH = h - bottom - 8f - paperY
    /** The scattered field: top and bottom of the filings' area. */
    val fieldTop = paperY + 118f * u
    val fieldBottom = paperY + paperH - 96f * u
    val cx = w / 2f
    val cy = (fieldTop + fieldBottom) / 2f
    /** Half length and half thickness of a bar magnet. */
    val half = 44f * u
    val thick = 13f * u
    /** Magnet B's resting x (past the right edge). */
    val parkX = w + 30f * u
    val footerY = paperY + paperH - 64f * u
}

/** Magnet poses at loop time t into out: [ax, ay, aAngle, bx, by, bAngle]. B's angle π puts its N on the left. */
internal fun ironMagnetsAt(g: IronGeo, t: Float, out: FloatArray) {
    val s = easeInOutSine(ironSeg(t, 1.4f, 5.4f))
    val ax0 = g.cx + g.paperW * 0.24f * sin(2 * PIF * s)
    val ay = g.cy + (g.fieldBottom - g.fieldTop) * 0.13f * sin(4 * PIF * s)
    val aAngle = 0.3f * sin(2 * PIF * s)
    val push = 40f * g.u * (easeOutBack(ironSeg(t, 5.55f, 6.6f)) - easeInOutCubic(ironSeg(t, 7.3f, 8.2f)))
    val inX = g.cx + 104f * g.u
    val bx = g.parkX + (inX - g.parkX) * (easeOutCubic(ironSeg(t, 5.4f, 6.4f)) - easeInOutCubic(ironSeg(t, 7.2f, 8.2f)))
    out[0] = ax0 - push
    out[1] = ay
    out[2] = aAngle
    out[3] = bx
    out[4] = g.cy
    out[5] = PIF
}

/** Field at (x, y) from both magnets, written to out[0], out[1]. */
internal fun ironFieldAt(x: Float, y: Float, m: FloatArray, half: Float, soft: Float, out: FloatArray) {
    var bx = 0f
    var by = 0f
    for (k in 0 until 2) {
        val mx = m[k * 3]
        val my = m[k * 3 + 1]
        val ca = cos(m[k * 3 + 2]) * half
        val sa = sin(m[k * 3 + 2]) * half
        var q = -1
        while (q <= 1) {
            val px = mx + q * ca
            val py = my + q * sa
            val dx = x - px
            val dy = y - py
            val r2 = dx * dx + dy * dy + soft
            val inv = q / (r2 * sqrt(r2))
            bx += dx * inv
            by += dy * inv
            q += 2
        }
    }
    out[0] = bx
    out[1] = by
}

/** The nearest pole to (x, y): out = [dx, dy, distance], pointing from the point to the pole. */
internal fun ironNearestPole(x: Float, y: Float, m: FloatArray, half: Float, out: FloatArray) {
    var best = 1e9f
    var bx = 0f
    var by = 0f
    for (k in 0 until 2) {
        val ca = cos(m[k * 3 + 2]) * half * 0.9f
        val sa = sin(m[k * 3 + 2]) * half * 0.9f
        var q = -1
        while (q <= 1) {
            val dx = m[k * 3] + q * ca - x
            val dy = m[k * 3 + 1] + q * sa - y
            val d = hypot(dx, dy)
            if (d < best) {
                best = d
                bx = dx
                by = dy
            }
            q += 2
        }
    }
    out[0] = bx
    out[1] = by
    out[2] = best
}

/** Wraps an angle difference into (−π/2, π/2]: a filing has no front or back. */
internal fun ironAxial(d: Float): Float {
    var a = d % PIF
    if (a > PIF / 2) a -= PIF
    if (a <= -PIF / 2) a += PIF
    return a
}

private fun ironHash(i: Int): Double {
    val s = sin(i * 12.9898 + 78.233) * 43758.5453
    return s - floor(s)
}

/** Filings scattered on a jittered grid over the field: x, y, resting angle, length (dp). */
internal class IronFilings(g: IronGeo) {
    val x = FloatArray(IRON_FILINGS)
    val y = FloatArray(IRON_FILINGS)
    val a0 = FloatArray(IRON_FILINGS)
    val len = FloatArray(IRON_FILINGS)

    init {
        val left = g.paperX + 14
        val right = g.paperX + g.paperW - 14
        val w = right - left
        val h = g.fieldBottom - g.fieldTop
        val cols = sqrt(IRON_FILINGS * w / h).roundToInt().coerceAtLeast(1)
        val rows = ceil(IRON_FILINGS / cols.toFloat()).toInt()
        for (i in 0 until IRON_FILINGS) {
            val c = i % cols
            val r = i / cols
            x[i] = left + ((c + 0.15f + 0.7f * ironHash(i).toFloat()) * w) / cols
            y[i] = g.fieldTop + ((r + 0.15f + 0.7f * ironHash(i + 1000).toFloat()) * h) / rows
            a0[i] = ironHash(i + 2000).toFloat() * PIF
            len[i] = (7f + 5f * ironHash(i + 3000).toFloat()) * g.u
        }
    }
}
