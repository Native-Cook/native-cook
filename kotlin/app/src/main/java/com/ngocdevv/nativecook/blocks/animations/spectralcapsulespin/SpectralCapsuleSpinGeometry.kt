package com.ngocdevv.nativecook.blocks.animations.spectralcapsulespin

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One full turn of the capsule, in seconds. */
const val LOOP = 10.0

/** A point on screen, in dp. */
data class ScsPoint(val x: Double, val y: Double)

/**
 * The capsule's pose and its projected outlines, from one tilt angle [phi].
 *
 * The capsule is a cylinder of radius [r] between two seams 2[h] apart, closed by two hemispheres.
 * It turns about the screen's anti-diagonal through [center]; at phi = 0 it lies flat along the
 * diagonal with end A at the top right, at phi = PI/2 end A points at the viewer. Every outline is
 * a polygon sampled the same way as the Swift reference.
 */
class SpectralCapsuleSpinGeometry(val center: ScsPoint, val r: Double, val h: Double, val phi: Double) {
    val sinP = sin(phi)
    val cosP = cos(phi)
    val facing: Double
    val cA: ScsPoint
    val cB: ScsPoint
    val rA: Double
    val rB: Double
    val nearIsA: Boolean
    val axis: ScsPoint
    val across: ScsPoint

    init {
        val x0 = cosP * U.x
        val y0 = cosP * U.y
        val z0 = sinP
        val dx = x0
        val dy = y0 * cos(TILT) - z0 * sin(TILT)
        val dz = y0 * sin(TILT) + z0 * cos(TILT)
        facing = abs(dz)
        val zA = h * dz
        val sA = FOCAL / (FOCAL - zA)
        val sB = FOCAL / (FOCAL + zA)
        cA = ScsPoint(center.x + dx * h * sA, center.y + dy * h * sA)
        cB = ScsPoint(center.x - dx * h * sB, center.y - dy * h * sB)
        rA = r * sA
        rB = r * sB
        nearIsA = dz >= 0
        val length = sqrt(dx * dx + dy * dy)
        axis = if (length > 1e-6) ScsPoint(dx / length, dy / length) else U
        across = ScsPoint(-axis.y, axis.x)
    }

    val nearC get() = if (nearIsA) cA else cB
    val nearR get() = if (nearIsA) rA else rB
    val farC get() = if (nearIsA) cB else cA
    val farR get() = if (nearIsA) rB else rA

    /** The screen direction from the near cap's centre towards its pole. */
    val outward get() = if (nearIsA) axis else ScsPoint(-axis.x, -axis.y)

    /** Convex hull of the two end discs: the silhouette. */
    fun silhouette(steps: Int = 40): List<ScsPoint> {
        val dx = cA.x - cB.x
        val dy = cA.y - cB.y
        val d = sqrt(dx * dx + dy * dy)
        if (d + min(rA, rB) <= max(rA, rB) + 0.01) {
            val c = if (rA >= rB) cA else cB
            val rr = max(rA, rB)
            return ellipse(c, rr, rr, across, axis, 0.0, 2 * PI, steps * 2)
        }
        val a = atan2(dy, dx)
        val g = acos(((rB - rA) / d).coerceIn(-1.0, 1.0))
        val points = ArrayList<ScsPoint>(steps * 2 + 2)
        for (i in 0..steps) {
            val t = a - g + 2 * g * i / steps
            points.add(ScsPoint(cA.x + rA * cos(t), cA.y + rA * sin(t)))
        }
        for (i in 0..steps) {
            val t = a + g + (2 * PI - 2 * g) * i / steps
            points.add(ScsPoint(cB.x + rB * cos(t), cB.y + rB * sin(t)))
        }
        return points
    }

    /** The near cap as seen: its outer half-disc joined to the front half of its seam ellipse. */
    fun nearCap(steps: Int = 32): List<ScsPoint> {
        val c = nearC
        val rn = nearR
        val o = outward
        val v = across
        val points = ArrayList<ScsPoint>(steps * 2)
        for (i in 0..steps) {
            val t = PI * i / steps
            points.add(ScsPoint(c.x + rn * (cos(t) * v.x + sin(t) * o.x), c.y + rn * (cos(t) * v.y + sin(t) * o.y)))
        }
        for (i in 1 until steps) {
            val t = PI + PI * i / steps
            points.add(
                ScsPoint(
                    c.x + rn * (cos(t) * v.x + facing * sin(t) * o.x),
                    c.y + rn * (cos(t) * v.y + facing * sin(t) * o.y),
                ),
            )
        }
        return points
    }

    /** The front half of the near seam: the edge between the lit cap and the body. */
    fun nearSeamFront(steps: Int = 32) = ellipse(nearC, nearR, nearR * facing, across, outward, PI, 2 * PI, steps)

    /** The outer arc of the near cap, inset by [inset], where the dispersion band sits. */
    fun nearRimArc(inset: Double, steps: Int = 32) =
        ellipse(nearC, nearR - inset, nearR - inset, across, outward, 0.0, PI, steps)

    private fun ringCentre(lambda: Double) =
        ScsPoint(farC.x + (nearC.x - farC.x) * lambda, farC.y + (nearC.y - farC.y) * lambda)

    /** A cross-section ring of the body at [lambda] (0 far seam, 1 near seam). */
    fun ring(lambda: Double, steps: Int = 48): List<ScsPoint> {
        val rr = farR + (nearR - farR) * lambda
        return ellipse(ringCentre(lambda), rr, rr * facing, across, axis, 0.0, 2 * PI, steps)
    }

    /** The half of a body ring on the far side, the part that stacks below the disc end-on. */
    fun ringBack(lambda: Double, steps: Int = 32): List<ScsPoint> {
        val rr = farR + (nearR - farR) * lambda
        return ellipse(ringCentre(lambda), rr, rr * facing, across, outward, PI, 2 * PI, steps)
    }

    /** Where the near cap's pole lands, pushed [k] radii along the outward direction. */
    fun towardsPole(k: Double): ScsPoint {
        val o = outward
        return ScsPoint(nearC.x + o.x * nearR * k, nearC.y + o.y * nearR * k)
    }

    companion object {
        val U = ScsPoint(0.70710678, -0.70710678)

        /** Camera distance in dp: a weak perspective. */
        const val FOCAL = 2000.0

        /** The camera looks down on the turn by this angle, so end-on the rings stack below the disc. */
        const val TILT = 0.09

        fun ellipse(
            c: ScsPoint, a: Double, b: Double, axisA: ScsPoint, axisB: ScsPoint,
            from: Double, to: Double, steps: Int,
        ): List<ScsPoint> = (0..steps).map { i ->
            val t = from + (to - from) * i / steps
            ScsPoint(c.x + a * cos(t) * axisA.x + b * sin(t) * axisB.x, c.y + a * cos(t) * axisA.y + b * sin(t) * axisB.y)
        }
    }
}

/** The light curves and the turn, as pure functions of the loop clock. */
object SpectralCapsuleSpinTimeline {
    /** One full turn: phi = 2PI u - 0.10 sin 4PI u, slower at the flat poses, faster end-on. */
    fun phi(t: Double): Double {
        val u = t / LOOP
        return 2 * PI * u - 0.10 * sin(4 * PI * u)
    }

    fun loopTime(seconds: Double): Double {
        val t = seconds % LOOP
        return if (t < 0) t + LOOP else t
    }

    fun bell(x: Double, c: Double, w: Double): Double {
        val k = (x - c) / w
        return exp(-k * k)
    }

    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    /** 1 when [phi] is at [target] (mod 2PI), falling off over about a quarter of a radian. */
    fun endOn(phi: Double, target: Double): Double {
        var d = (phi - target) % (2 * PI)
        if (d < -PI) d += 2 * PI
        if (d > PI) d -= 2 * PI
        return bell(d, 0.0, 0.45)
    }

    fun hash(i: Double, k: Double): Double {
        val x = sin(i * 12.9898 + k * 78.233) * 43758.5453
        return x - floor(x)
    }

    /** Rainbow band, warm caustic, frost, end-on rings, lit fibres for a facing of [c]. */
    class Light(c: Double) {
        val rainbow = bell(c, 0.40, 0.18) * smoothstep(0.10, 0.22, c)
        val warm = bell(c, 0.72, 0.17) * (1 - smoothstep(0.80, 0.99, c))
        val frost = bell(c, 0.68, 0.24)
        val rings = smoothstep(0.80, 0.99, c)
        val fibres = 0.6 * bell(c, 0.95, 0.05)
        val rim = 1 - 0.3 * rings
    }
}
