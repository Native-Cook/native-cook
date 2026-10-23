package com.ngocdevv.nativecook.blocks.onboarding.metacardunifyfan

import kotlin.math.*

/** Native translation of the source's reversible 1,900 ms transformation. */
internal object MetacardTimeline {
    data class Loop(val u: Double, val forward: Double, val spin: Double)

    data class Leaf(
        val x: Double,
        val y: Double,
        val rotation: Double,
        val scale: Double,
        val opacity: Double,
        val progress: Double,
    )

    data class Hub(
        val morph: Double,
        val scale: Double,
        val bumpY: Double,
        val roll: Double,
        val mark: Double,
    )

    data class Pose(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val yaw: Double,
        val pitch: Double,
        val roll: Double,
        val lift: Double,
    )

    val colors =
        arrayOf(0xFF6B1A, 0xEA1179, 0x3D6BE0, 0x39C2B4, 0xF4F4F2, 0xF2B24A, 0x7A2BE2, 0x35D07F)
            .flatMapIndexed { i, c ->
                listOf(c shr 16 and 255, c shr 8 and 255, c and 255).map {
                    (it / 255.0 * if (i == 4) .55 else 1.0).toFloat()
                }
            }
            .toFloatArray()
    private val gloss = doubleArrayOf(.22, .34, .28, .10, .16, .40, .24, .10)

    fun clamp(x: Double, lo: Double = 0.0, hi: Double = 1.0) = x.coerceIn(lo, hi)

    fun lerp(a: Double, b: Double, p: Double) = a + (b - a) * p

    fun phase(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))

    fun radians(x: Double) = x * PI / 180

    private fun cubic(p: Double, a: Double, b: Double) =
        3 * (1 - p) * (1 - p) * p * a + 3 * (1 - p) * p * p * b + p * p * p

    private fun bezier(p: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        if (p <= 0 || p >= 1) return clamp(p)
        var lo = 0.0
        var hi = 1.0
        repeat(16) {
            val mid = (lo + hi) / 2
            if (cubic(mid, x1, x2) < p) lo = mid else hi = mid
        }
        return cubic((lo + hi) / 2, y1, y2)
    }

    fun ease(p: Double) = bezier(p, .23, 1.0, .32, 1.0)

    fun fold(p: Double) = bezier(p, .77, 0.0, .175, 1.0)

    fun spring(seconds: Double): Double {
        val t = max(0.0, seconds)
        val z = .62
        val w = 9.5
        val wd = w * sqrt(1 - z * z)
        return 1 - exp(-z * w * t) * (cos(wd * t) + z * w / wd * sin(wd * t))
    }

    fun liftState(t: Double) = lerp(spring((t - 1060) / 1000), 1.0, ease(phase(t, 1800.0, 1900.0)))

    fun loopState(seconds: Double): Loop {
        val t = ((seconds % 12) + 12) % 12
        val u =
            when {
                t < 3 -> 0.0
                t < 4.9 -> (t - 3) / 1.9
                t < 9.4 -> 1.0
                t < 10.8 -> 1 - (t - 9.4) / 1.4
                else -> 0.0
            }
        return Loop(clamp(u), if (t < 9.4) 1.0 else 0.0, -49 + 7.5 * (if (t < 7) t else t - 12))
    }

    fun reducedLoopState(seconds: Double): Pair<Double, Double> {
        val t = ((seconds % 12) + 12) % 12
        val u =
            when {
                t < 3.12 -> 0.0
                t < 4.9 -> phase(t, 3.12, 3.44)
                t < 9.52 -> 1.0
                else -> 1 - phase(t, 9.52, 9.84)
            }
        val press =
            if (t < 9.4) phase(t, 3.0, 3.12) * (1 - phase(t, 3.12, 3.30))
            else phase(t, 9.4, 9.52) * (1 - phase(t, 9.52, 9.70))
        return u to press
    }

    fun fanCardPose(i: Int, spin: Double, u: Double, anchorSpin: Double = spin): Leaf {
        val t = u * 1900
        val start = 260.0 + 24 * i
        val end = start + 500
        val p = fold(phase(t, start, end))
        val breath = ease(phase(t, 0.0, 260.0))
        val angle = spin + 45 * i - 5 * breath
        val orbit = radians(angle + 135 * p)
        val radius = (132 + 8 * breath) * (1 - p) * lerp(1.0, 176.0 / 144, p)
        val target = ceil((anchorSpin + 45 * i) / 180) * 180
        val arrival = phase(t, end, end + 160)
        val square = fold(phase(t, 1025.0, 1110.0))
        val residual = (i - 3.5) * 11 * lerp(1.0, .85, phase(t, end, 1025.0))
        val overshoot = 7 * p * (1 - ease(arrival))
        val spread = fold(phase(t, end - 150, end))
        val rotation =
            lerp(lerp(angle - 90, target, p) + residual * spread + overshoot, target, square)
        val scale = lerp(1.0, 176.0 / 144, p) * (1 + .16 * spread * (1 - square))
        return Leaf(
            201 + sin(orbit) * radius,
            356 - cos(orbit) * radius + 166 * liftState(t),
            rotation,
            scale,
            1 - phase(t, 1100.0, 1110.0),
            p,
        )
    }

    fun hubState(u: Double, spin: Double, forward: Double, pressed: Double): Hub {
        val t = u * 1900
        val morph = fold(phase(t, 300.0, 760.0))
        val press = if (t < 140) ease(phase(t, 0.0, 140.0)) else 1 - ease(phase(t, 140.0, 300.0))
        var bumps = 0.0
        if (forward != 0.0)
            for (i in 0..7) {
                val b = phase(t, 760.0 + 24 * i, 850.0 + 24 * i)
                bumps += sin(PI * b) * (1 - b) * .024
            }
        val lock = if (forward != 0.0) sin(PI * phase(t, 1025.0, 1110.0)) * .03 else 0.0
        val turn = spin - 5 * ease(phase(t, 0.0, 260.0))
        val nearest = floor(turn / 180 + .5) * 180
        return Hub(
            morph,
            (1 - .06 * press + bumps - lock) * (1 - lerp(.06, .03, morph) * pressed),
            -bumps * 75,
            (turn - nearest) * (1 - morph),
            fold(phase(t, 620.0, 1000.0)),
        )
    }

    fun heroPose(
        u: Double,
        time: Double,
        dragYaw: Double,
        dragPitch: Double,
        reduced: Boolean,
    ): Pose {
        val t = u * 1900
        val s = liftState(t)
        val idle = ease(phase(t, 1800.0, 1900.0))
        return Pose(
            201.0,
            lerp(356.0, 522.0, s) + (if (reduced) 0.0 else 3 * sin(time * PI * 2 / 5.8) * idle),
            lerp(lerp(134.0, 184.0, fold(phase(t, 260.0, 760.0))), 304.0, s),
            lerp(lerp(85.0, 116.6, fold(phase(t, 260.0, 760.0))), 192.0, s),
            -26 * s + (dragYaw + if (reduced) 0.0 else 6 * sin(time * PI * 2 / 6.4)) * idle,
            13 * s + (dragPitch + if (reduced) 0.0 else 2 * sin(time * PI * 2 / 5.2 + 1)) * idle,
            -1.5 * s,
            s,
        )
    }

    fun effectsState(
        u: Double,
        forward: Double,
        time: Double,
        reduced: Boolean,
    ): Map<String, Double> {
        val t = u * 1900
        val one = if (forward != 0.0 && !reduced) 1.0 else 0.0
        return mapOf(
            "pageMix" to clamp(liftState(t)),
            "darkness" to 1 - phase(t, 300.0, 900.0),
            "bloom" to
                one *
                    .8 *
                    ease(phase(t, 1025.0, 1040.0)) *
                    (1 - phase(t, 1040.0, 1500.0)).pow(2) *
                    lerp(1.0, .45, ease(phase(t, 1040.0, 1110.0))),
            "bloomReach" to lerp(46.0, 150.0, phase(t, 1040.0, 1500.0)),
            "inner" to one * .85 * ease(phase(t, 1025.0, 1040.0)) * (1 - phase(t, 1040.0, 1180.0)),
            "lamination" to one * (if (t >= 1060) 1 - ease(phase(t, 1300.0, 1900.0)) else 0.0),
            "sweep" to phase(t, 1080.0, 1700.0),
            "tint" to one * ease(phase(t, 1080.0, 1110.0)) * (1 - phase(t, 1240.0, 1700.0)),
            "chip" to ease(phase(t, 1150.0, 1500.0)),
            "halo" to if (u == 0.0 && !reduced) .035 + .019 * sin(time * PI * 2 / 2.4) else 0.0,
        )
    }

    fun rotationMatrix(yaw: Double, pitch: Double, roll: Double): DoubleArray {
        val cy = cos(radians(yaw))
        val sy = sin(radians(yaw))
        val cp = cos(radians(pitch))
        val sp = sin(radians(pitch))
        val cr = cos(radians(roll))
        val sr = sin(radians(roll))
        return doubleArrayOf(
            cr * cy,
            sr * cy,
            -sy,
            cr * sy * sp - sr * cp,
            sr * sy * sp + cr * cp,
            cy * sp,
            cr * sy * cp + sr * sp,
            sr * sy * cp - cr * sp,
            cy * cp,
        )
    }

    private fun reflectionAt(r: DoubleArray, x: Double, y: Double): Double {
        val wx = r[0] * x + r[3] * y
        val wy = r[1] * x + r[4] * y
        val wz = r[2] * x + r[5] * y - 555
        val len = sqrt(wx * wx + wy * wy + wz * wz)
        val dot = (wx * r[6] + wy * r[7] + wz * r[8]) / len
        return wy / len - 2 * dot * r[7] + .9 * (wx / len - 2 * dot * r[6])
    }

    fun values(vararg numbers: Double) = FloatArray(numbers.size) { numbers[it].toFloat() }

    fun fanUniforms(
        i: Int,
        spin: Double,
        u: Double,
        press: Double,
        seam: Boolean,
        anchorSpin: Double,
    ): Map<String, FloatArray> {
        val p = fanCardPose(i, spin, u, anchorSpin)
        val last = fanCardPose(7, spin, u, anchorSpin)
        val first = fanCardPose(0, spin, u, anchorSpin)
        return mapOf(
            "centre" to values(p.x, p.y),
            "angle" to values(radians(p.rotation)),
            "scale" to values(p.scale),
            "opacity" to values(p.opacity),
            "gloss" to values(gloss[i]),
            "brushed" to values(if (i == 2) .6 else 0.0),
            "ao" to values(1 - p.progress),
            "press" to values(press),
            "seam" to values(if (seam) 1.0 else 0.0),
            "isLast" to values(if (i == 7) 1.0 else 0.0),
            "first" to values(first.x, first.y, radians(first.rotation)),
            "last" to values(last.x, last.y, radians(last.rotation)),
            "firstScale" to values(first.scale),
            "lastScale" to values(last.scale),
            "shadowHandover" to values(1 - ease(phase(u * 1900, 495.0, 505.0))),
            "handover" to values(if (u * 1900 < 505) 1.0 else 0.0),
        )
    }

    fun heroUniforms(
        u: Double,
        spin: Double,
        time: Double,
        forward: Double,
        press: Double = 0.0,
        dragYaw: Double = 0.0,
        dragPitch: Double = 0.0,
        reduced: Boolean = false,
    ): Map<String, FloatArray> {
        val h = hubState(u, spin, forward, press)
        val p = heroPose(u, time, dragYaw, dragPitch, reduced)
        val e = effectsState(u, forward, time, reduced)
        val size = p.width / 304
        val markSize = lerp(34.0, 36 * size, h.mark)
        val roll = h.roll + p.roll
        val r = rotationMatrix(p.yaw, p.pitch, roll)
        var left = Double.POSITIVE_INFINITY
        var right = Double.NEGATIVE_INFINITY
        var top = Double.POSITIVE_INFINITY
        var bottom = Double.NEGATIVE_INFINITY
        for (i in 0..3) {
            val x = (if (i < 2) -1 else 1) * p.width / 2 * h.scale
            val y = (if (i % 2 != 0) -1 else 1) * p.height / 2 * h.scale
            val projection = 555 / (555 - r[2] * x - r[5] * y)
            val px = (r[0] * x + r[3] * y) * projection
            val py = (r[1] * x + r[4] * y) * projection
            left = min(left, px)
            right = max(right, px)
            top = min(top, py)
            bottom = max(bottom, py)
        }
        val hx = p.width / 2 * h.scale
        val hy = p.height / 2 * h.scale
        val fraction = .46 - .16 * sin(clamp((p.yaw + 26) / 6, -1.0, 1.0) * PI / 2)
        val along = fraction * 2 - 1
        val lead = 80 / hypot(hx * 2, hy * 2)
        return mapOf(
            "centre" to values(p.x - (left + right) / 2, p.y + h.bumpY - (top + bottom) / 2),
            "halfSize" to values(hx, hy),
            "octagon" to values(62 * h.scale),
            "morph" to values(h.morph),
            "corner" to values(lerp(9.6, 13.0, clamp(p.lift)) * h.scale),
            "thickness" to values(3.2),
            "camera" to values(555.0),
            "rot" to values(*r),
            "mark" to
                values(
                    110 * size * h.mark * h.scale,
                    -54 * size * h.mark * h.scale,
                    markSize * h.scale,
                ),
            "markTurn" to values(-radians(roll) * (1 - h.mark)),
            "chipAlpha" to values(e.getValue("chip")),
            "lamination" to values(e.getValue("lamination")),
            "sweep" to values(e.getValue("sweep")),
            "tint" to values(e.getValue("tint")),
            "colors" to colors,
            "studio" to
                values(
                    lerp(-1.1, reflectionAt(r, hx * along, hy * along), clamp(p.lift)),
                    lerp(
                        -1.06,
                        reflectionAt(r, hx * (along + lead), hy * (along + lead)),
                        clamp(p.lift),
                    ),
                ),
            "shadowBlur" to values(lerp(10.0, 36.0, clamp(p.lift))),
            "inner" to values(e.getValue("inner")),
            "lightAngle" to values(radians(spin)),
        )
    }

    fun bounceUniforms(
        u: Double,
        spin: Double,
        anchorSpin: Double,
        reduced: Boolean,
    ): Map<String, FloatArray> {
        val progress = if (reduced) 0.0 else u
        val pools = ArrayList<Float>()
        for (i in 0..7) {
            val p = fanCardPose(i, spin, progress, anchorSpin)
            val orbit =
                radians(
                    spin + 45 * i - 5 * ease(phase(progress * 1900, 0.0, 260.0)) + 135 * p.progress
                )
            val offset = 84 * (1 - p.progress)
            pools.add((p.x + sin(orbit) * offset).toFloat())
            pools.add((p.y - cos(orbit) * offset).toFloat())
        }
        return mapOf(
            "pools" to pools.toFloatArray(),
            "colors" to colors,
            "amount" to values(if (reduced) 1 - u else 1 - phase(u * 1900, 1025.0, 1110.0)),
            "darkness" to values(if (reduced) 1.0 else 1 - phase(u * 1900, 300.0, 900.0)),
        )
    }
}
