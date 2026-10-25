package com.ngocdevv.nativecook.blocks.playground.slinkystairs

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Slinky Stairs — the motion, ported from slinkyMotion.ts. The slinky is 40 rings. Each frame places
 every ring (centre and the direction of its long axis) from a pure function of the loop time: a
 ring is in the stack it is leaving, in the arch, or in the stack it is building. The arch is a
 cubic Bézier between the two stacks' tops whose end controls follow the end rings' axes, so the
 flipping end turns over smoothly.

 autoPlay (LOOP = 9 s):
   0.0 – 1.2   rest on the top step.
   1.2 – 6.4   three steps, 1.733 s each: tip over (30 %: the top end arcs over and lands on the
               next step, its axis turning 180°), pour (52 %: coils flow through the arch), flip
               (18 %: the last end leaves the old step and lands on top of the new stack).
   6.4 – 7.0   settle on the bottom step (a damped squash, 5.5 Hz).
   7.0 – 8.4   lifted back to the top along an arc by an unseen hand holding the top ring: the
               coils hang and stretch (up to 1.9x) and swing behind the motion; set down with a squash.
   8.4 – 9.0   rest. 9.0 is the same frame as 0.0.
*/

internal const val LOOP = 9.0
internal const val SLINKY_N = 40
internal const val SLINKY_STEPS = 3
internal const val SLINKY_WALK_FROM = 1.2
internal const val SLINKY_STEP_FOR = (6.4 - 1.2) / 3
internal const val SLINKY_LIFT_FROM = 7.0
internal const val SLINKY_LIFT_FOR = 1.4
/** Coils that live in the arch while it pours. */
private const val ARCH = 12
private const val TIP = 0.3
private const val POUR = 0.82

internal class SlinkyGeo(
    val w: Double,
    val h: Double,
    /** Stack-bottom centre on each step (0 = top step). */
    val baseX: DoubleArray,
    val baseY: DoubleArray,
    /** Ring radius (dp), stacked pitch (dp), and the camera's tilt (minor / major). */
    val R: Double,
    val pitch: Double,
    val tilt: Double,
    val tread: Double,
    val riser: Double,
)

/** A frame: per ring x, y, nx, ny (the unit long axis); contact shadows x, y, half width, alpha; the label's roll. */
internal class SlinkyPose {
    val rings = DoubleArray(SLINKY_N * 4)
    var ringCount = 0
    val shadows = DoubleArray(8)
    var shadowCount = 0
    var label = 0.0

    fun ring(x: Double, y: Double, tx: Double, ty: Double) {
        // The long axis is perpendicular to the slinky's axis.
        val o = ringCount * 4
        rings[o] = x
        rings[o + 1] = y
        rings[o + 2] = -ty
        rings[o + 3] = tx
        ringCount += 1
    }

    fun shadow(x: Double, y: Double, r: Double, a: Double) {
        val o = shadowCount * 4
        shadows[o] = x
        shadows[o + 1] = y
        shadows[o + 2] = r
        shadows[o + 3] = a
        shadowCount += 1
    }

    fun clear() {
        ringCount = 0
        shadowCount = 0
    }
}

internal object SlinkyMotion {
    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeInOutCubic(x: Double): Double {
        val k = clamp01(x)
        return if (k < 0.5) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2
    }

    private val tmp = DoubleArray(4)

    /** Point and tangent of a cubic Bézier at w: [x, y, tx, ty]. */
    private fun bez(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double, dx: Double, dy: Double, w: Double, out: DoubleArray) {
        val u = 1 - w
        out[0] = u * u * u * ax + 3 * u * u * w * bx + 3 * u * w * w * cx + w * w * w * dx
        out[1] = u * u * u * ay + 3 * u * u * w * by + 3 * u * w * w * cy + w * w * w * dy
        var tx = 3 * u * u * (bx - ax) + 6 * u * w * (cx - bx) + 3 * w * w * (dx - cx)
        var ty = 3 * u * u * (by - ay) + 6 * u * w * (cy - by) + 3 * w * w * (dy - cy)
        val l = hypot(tx, ty)
        if (l < 1e-4) {
            tx = 0.0
            ty = -1.0
        } else {
            tx /= l
            ty /= l
        }
        out[2] = tx
        out[3] = ty
    }

    /** A stack standing at (x, y): ring i at height i * pitch. */
    private fun stackRings(out: SlinkyPose, x: Double, y: Double, pitch: Double) {
        for (i in 0 until SLINKY_N) out.ring(x, y - i * pitch, 0.0, -1.0)
    }

    /**
     * The general walking slinky: `n1` coils stacked at A (first end at the bottom), `n2` coils
     * stacked at B (last end at the bottom), and the rest in an arch from (startX, startY) to
     * (endX, endY). Directions are the end rings' axes.
     */
    private fun walkRings(
        g: SlinkyGeo, ax: Double, ay: Double, bx: Double, by: Double, n1: Double, n2: Double,
        startX: Double, startY: Double, startDir: Double, endX: Double, endY: Double, endDir: Double, out: SlinkyPose,
    ) {
        val p = g.pitch
        val n = SLINKY_N
        val archCount = n - n1 - n2
        val span = hypot(endX - startX, endY - startY)
        // Controls grow with the arch, so a short arch (an end just lifting) never loops back on itself.
        val h = max(1.0, min(170.0, span * 0.55 + archCount * 1.5))
        val sdx = cos(startDir)
        val sdy = sin(startDir)
        val edx = cos(endDir)
        val edy = sin(endDir)
        for (i in 0 until n) {
            if (i < n1 - 0.5) {
                out.ring(ax, ay - i * p, 0.0, -1.0)
            } else if (i > n - 1 - n2 + 0.5) {
                out.ring(bx, by - (n - 1 - i) * p, 0.0, -1.0)
            } else {
                val lo = max(0.0, n1 - 0.5)
                val hi = min(n - 1.0, n - 1 - n2 + 0.5)
                val w = if (hi - lo < 1e-3) 0.5 else clamp01((i - lo) / (hi - lo))
                bez(startX, startY, startX + sdx * h, startY + sdy * h, endX - edx * h, endY - edy * h, endX, endY, w, tmp)
                out.ring(tmp[0], tmp[1], tmp[2], tmp[3])
            }
        }
    }

    /** One step from step k to k + 1, u in 0..1. */
    private fun stepRings(g: SlinkyGeo, k: Int, u: Double, out: SlinkyPose) {
        val p = g.pitch
        val ax = g.baseX[k]
        val ay = g.baseY[k]
        val bx = g.baseX[k + 1]
        val by = g.baseY[k + 1]
        val hh = (SLINKY_N - 1) * p
        val up = -PI / 2
        if (u < TIP) {
            // The top end tips over and lands on the next step.
            val v = easeInOutCubic(u / TIP)
            val nA = ARCH * min(1.0, v * 1.4)
            val n1 = SLINKY_N - nA
            val x0 = ax
            val y0 = ay - hh
            val ex = x0 + (bx - x0) * (3 * (1 - v) * v * v * 0.95 + v * v * v) + (1 - v) * (1 - v) * v * 3 * g.tread * 0.25
            val ey = (1 - v).pow(3) * y0 + 3 * (1 - v) * (1 - v) * v * (y0 - g.riser * 0.9) + 3 * (1 - v) * v * v * (by - g.riser * 1.25) + v * v * v * by
            walkRings(g, ax, ay, bx, by, n1, 0.0, ax, ay - (n1 - 1) * p, up, ex, ey, up + PI * v, out)
            return
        }
        if (u < POUR) {
            // Coils pour through the arch.
            val v = easeInOutCubic((u - TIP) / (POUR - TIP))
            val n1 = (SLINKY_N - ARCH) * (1 - v)
            val n2 = (SLINKY_N - ARCH) * v
            walkRings(g, ax, ay, bx, by, n1, n2, ax, ay - max(0.0, n1 - 1) * p, up, bx, by - max(0.0, n2 - 1) * p, PI / 2, out)
            return
        }
        // The first end leaves the old step and flips over onto the new stack.
        val v = easeInOutCubic((u - POUR) / (1 - POUR))
        val n2 = SLINKY_N - ARCH * (1 - v)
        val topB = by - (SLINKY_N - 1) * p
        val q = 1 - v
        val fx = q * q * q * ax + 3 * q * q * v * (ax + g.tread * 0.12) + 3 * q * v * v * (bx - g.tread * 0.35) + v * v * v * bx
        val fy = q * q * q * ay + 3 * q * q * v * (ay - g.riser * 0.55) + 3 * q * v * v * (topB - 34) + v * v * v * topB
        walkRings(g, ax, ay, bx, by, 0.0, n2, fx, fy, up + PI * v, bx, by - max(0.0, n2 - 1) * p, PI / 2, out)
    }

    /** A stack squashing after a landing: e seconds since. */
    private fun squash(e: Double): Double {
        if (e < 0 || e > 0.8) return 1.0
        return 1 - 0.2 * exp(-e / 0.14) * cos(2 * PI * 5.5 * e)
    }

    /** Lifted along an arc from step k to step 0 (v in 0..1): the coils hang from the top ring. */
    fun liftRings(g: SlinkyGeo, k: Int, v: Double, out: SlinkyPose) {
        val e = easeInOutCubic(v)
        val hh = (SLINKY_N - 1) * g.pitch
        val x0 = g.baseX[k]
        val y0 = g.baseY[k] - hh
        val x1 = g.baseX[0]
        val y1 = g.baseY[0] - hh
        val apexY = min(y0, y1) - g.riser * 1.3
        val topX = x0 + (x1 - x0) * e
        val topY = (1 - e) * (1 - e) * y0 + 2 * (1 - e) * e * apexY + e * e * y1
        // Velocity of the hand (for the swing) from the curve's derivative.
        val de = 6 * v * (1 - v)
        val vx = (x1 - x0) * de
        val stretch = 1 + 0.9 * sin(PI * e)
        for (i in 0 until SLINKY_N) {
            val d = (SLINKY_N - 1.0 - i) / (SLINKY_N - 1)
            val lag = -vx * 0.09 * d * d
            out.ring(topX + lag, topY + (SLINKY_N - 1 - i) * g.pitch * stretch, lag * 0.004, -1.0)
        }
    }

    /** The finger holding the top ring at (px, py): the slinky stretches from its stack at step k. */
    fun stretchRings(g: SlinkyGeo, k: Int, px: Double, py: Double, out: SlinkyPose) {
        val bx = g.baseX[k]
        val by = g.baseY[k]
        val l = hypot(px - bx, py - by)
        val hh = (SLINKY_N - 1) * g.pitch
        if (l <= hh) {
            // Squashed (or only leaning): a straight, tilted stack.
            val tx = if (l > 1e-3) (px - bx) / l else 0.0
            val ty = if (l > 1e-3) (py - by) / l else -1.0
            for (i in 0 until SLINKY_N) {
                val f = i / (SLINKY_N - 1.0)
                out.ring(bx + (px - bx) * f, by + (py - by) * f, tx, ty)
            }
            return
        }
        val h = l * 0.32
        for (i in 0 until SLINKY_N) {
            val w = (i / (SLINKY_N - 1.0)).pow(1.5)
            bez(bx, by, bx, by - h, px, py + h, px, py, w, tmp)
            out.ring(tmp[0], tmp[1], tmp[2], tmp[3])
        }
    }

    /** Step label value for the loop (0 Ready, 1 – 3 Step k, 4 Landed, 5 Ready again). */
    private fun loopLabel(t: Double): Double {
        var v = 0.0
        for (s in 0 until SLINKY_STEPS) v += easeInOutCubic(seg(t, SLINKY_WALK_FROM + s * SLINKY_STEP_FOR, SLINKY_WALK_FROM + s * SLINKY_STEP_FOR + 0.3))
        v += easeInOutCubic(seg(t, 6.4, 6.7))
        v += easeInOutCubic(seg(t, SLINKY_LIFT_FROM, SLINKY_LIFT_FROM + 0.3))
        return v
    }

    fun loopPose(t: Double, g: SlinkyGeo, out: SlinkyPose): SlinkyPose {
        out.clear()
        if (t < SLINKY_WALK_FROM) {
            stackRings(out, g.baseX[0], g.baseY[0], g.pitch * squash(t + LOOP - 8.4))
            out.shadow(g.baseX[0], g.baseY[0], g.R, 1.0)
        } else if (t < 6.4) {
            val s = min(SLINKY_STEPS - 1, floor((t - SLINKY_WALK_FROM) / SLINKY_STEP_FOR).toInt())
            val u = (t - SLINKY_WALK_FROM - s * SLINKY_STEP_FOR) / SLINKY_STEP_FOR
            stepRings(g, s, u, out)
            val leave = if (u < POUR) 1 - seg(u, TIP, POUR) * 0.6 else 0.4 * (1 - seg(u, POUR, POUR + 0.08))
            val arrive = if (u < TIP) seg(u, TIP * 0.8, TIP) * 0.5 else 0.5 + 0.5 * seg(u, TIP, 1.0)
            out.shadow(g.baseX[s], g.baseY[s], g.R, leave)
            out.shadow(g.baseX[s + 1], g.baseY[s + 1], g.R, arrive)
        } else if (t < SLINKY_LIFT_FROM) {
            stackRings(out, g.baseX[SLINKY_STEPS], g.baseY[SLINKY_STEPS], g.pitch * squash(t - 6.4))
            out.shadow(g.baseX[SLINKY_STEPS], g.baseY[SLINKY_STEPS], g.R, 1.0)
        } else if (t < SLINKY_LIFT_FROM + SLINKY_LIFT_FOR) {
            val v = (t - SLINKY_LIFT_FROM) / SLINKY_LIFT_FOR
            liftRings(g, SLINKY_STEPS, v, out)
            val e = easeInOutCubic(v)
            out.shadow(g.baseX[SLINKY_STEPS], g.baseY[SLINKY_STEPS], g.R, 1 - seg(v, 0.0, 0.25))
            out.shadow(g.baseX[0], g.baseY[0], g.R * (1.4 - 0.4 * e), seg(v, 0.7, 1.0))
        } else {
            stackRings(out, g.baseX[0], g.baseY[0], g.pitch * squash(t - 8.4))
            out.shadow(g.baseX[0], g.baseY[0], g.R, 1.0)
        }
        out.label = loopLabel(t)
        return out
    }
}
