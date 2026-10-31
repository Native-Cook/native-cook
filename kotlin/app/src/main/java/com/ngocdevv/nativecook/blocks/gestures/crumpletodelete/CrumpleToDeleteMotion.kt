/** Deterministic mesh deformation and the nine-second ctd-motion.ts autoplay script. */
package com.ngocdevv.nativecook.blocks.gestures.crumpletodelete

import kotlin.math.*

/** Direct translation of ctd-motion.ts. All script poses depend only on loop time. */
internal object CrumpleToDeleteMotion {
    const val LOOP = 9.0
    const val GX = 13
    const val GY = 16
    const val DRAG_FULL = 300.0
    const val REST = 0
    const val DRAG = 1
    const val BACK = 2
    const val GONE = 3
    const val UNDO = 4

    data class Geo(val hx: Double, val hy: Double, val bx: Double, val by: Double)

    data class Pose(
        val x: Double,
        val y: Double,
        val c: Double = 0.0,
        val spin: Double = 0.0,
        val sc: Double = 1.0,
        val sink: Double = 0.0,
        val wobble: Double = 0.0,
        val toast: Double = 0.0,
        val lift: Double = 0.0,
    )

    fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)

    fun smooth(a: Double, b: Double, x: Double): Double {
        val t = seg(x, a, b)
        return t * t * (3 - 2 * t)
    }

    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    fun easeOut(x: Double) = 1 - (1 - x).pow(3)

    fun springStep(t: Double, duration: Double, ratio: Double): Double {
        if (t <= 0) return 0.0
        val w = 2 * PI / duration
        if (ratio >= 1) return 1 - exp(-w * t) * (1 + w * t)
        val wd = w * sqrt(1 - ratio * ratio)
        return 1 - exp(-ratio * w * t) * (cos(wd * t) + ratio * w / wd * sin(wd * t))
    }

    fun springTo(
        x: Double,
        v: Double,
        target: Double,
        duration: Double,
        ratio: Double,
        h: Double,
    ): Pair<Double, Double> {
        val w = 2 * PI / duration
        val nv = v + (w * w * (target - x) - 2 * ratio * w * v) * h
        return Pair(x + nv * h, nv)
    }

    fun hash(i: Double): Double {
        val s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    fun tossPos(q: Double, x0: Double, y0: Double, x1: Double, y1: Double): Pair<Double, Double> {
        val apex = min(y0, y1) - 120
        return Pair(x0 + (x1 - x0) * q, (1 - q).pow(2) * y0 + 2 * (1 - q) * q * apex + q * q * y1)
    }

    /** Returns the complete autoplay pose from loop time without frame-dependent accumulation. */
    fun script(t: Double, g: Geo): Pose {
        if (t < 1 || t >= 8) return Pose(g.hx, g.hy)
        if (t < 2.55) {
            val u = easeInOut(seg(t, 1.0, 2.55))
            val dy = 210 * u
            return Pose(
                g.hx + 18 * u,
                g.hy + dy,
                min(1.0, dy / DRAG_FULL),
                0.12 * u,
                lift = smooth(1.0, 1.2, t),
            )
        }
        val tr = t - 2.55
        val tu = t - 6
        if (tu < 0) {
            val c0 = 210 / DRAG_FULL
            val c = c0 + (1 - c0) * springStep(tr, 0.42, 0.78)
            val q = easeInOut(seg(tr, 0.28, 0.9))
            val p = tossPos(q, g.hx + 18, g.hy + 210, g.bx, g.by)
            val land = tr - 0.9
            return Pose(
                p.first,
                p.second,
                c,
                0.12 + 7 * q,
                1 - 0.18 * q,
                easeInOut(seg(tr, 0.9, 1.25)),
                if (land > 0) exp(-land * 5) * sin(land * 26) else 0.0,
                smooth(1.0, 1.3, tr),
                1 - smooth(0.0, 0.3, tr),
            )
        }
        val q = easeInOut(seg(tu, 0.18, 0.72))
        val p = tossPos(q, g.bx, g.by, g.hx, g.hy)
        val open = springStep(tu - 0.62, 0.8, 0.62)
        return Pose(
            p.first,
            p.second,
            max(0.0, 1 - open),
            7.12 * (1 - q) * (1 - smooth(0.0, 1.0, open)),
            0.82 + 0.18 * q,
            1 - easeOut(seg(tu, 0.0, 0.22)),
            toast = 1 - smooth(0.0, 0.25, tu),
        )
    }

    /** Positions and warm facet shading, using the same seeded wrinkles as the RN mesh. */
    fun crumple(
        c: Double,
        spin: Double,
        sc: Double,
        cx: Double,
        cy: Double,
        nw: Double,
        nh: Double,
        pos: FloatArray,
        colors: IntArray,
    ) {
        val e = c * c * (3 - 2 * c)
        val wr = sin(PI * min(1.0, c * 1.25))
        val cs = cos(spin)
        val sn = sin(spin)
        for (j in 0 until GY) for (i in 0 until GX) {
            val id = j * GX + i
            val u = i.toDouble() / (GX - 1) * 2 - 1
            val v = j.toDouble() / (GY - 1) * 2 - 1
            val r1 = hash(id.toDouble()) * 2 - 1
            val r2 = hash(id + 91.7) * 2 - 1
            val r3 = hash(id + 33.1)
            val x0 = u * nw / 2
            val y0 = v * nh / 2
            val du = u * sqrt(1 - v * v / 2)
            val dv = v * sqrt(1 - u * u / 2)
            val edge = max(abs(u), abs(v))
            val rb = 46 * (0.72 + 0.28 * r3) * (1 - 0.18 * edge * edge)
            val bx = du * rb + r1 * 5
            val by = dv * rb + r2 * 5
            val g = 1 - 0.45 * e
            val x = (x0 * g + (bx - x0 * g) * e.pow(1.4) + r1 * 13 * wr) * sc
            val y = (y0 * g + (by - y0 * g) * e.pow(1.4) + r2 * 13 * wr) * sc
            pos[id * 2] = (cx + x * cs - y * sn).toFloat()
            pos[id * 2 + 1] = (cy + x * sn + y * cs).toFloat()
            val facing = 0.5 + 0.5 * (r1 * 0.7 - r2 * 0.7)
            val b = (1 - c * (0.62 - 0.6 * facing) - c * 0.16 * edge).coerceIn(0.0, 1.0)
            colors[id] =
                android.graphics.Color.rgb(
                    (255 * b.pow(0.85)).roundToInt(),
                    (255 * b).roundToInt(),
                    (255 * b.pow(1.25)).roundToInt(),
                )
        }
    }
}
