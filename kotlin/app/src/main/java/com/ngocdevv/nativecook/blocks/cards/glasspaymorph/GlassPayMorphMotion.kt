package com.ngocdevv.nativecook.blocks.cards.glasspaymorph

import kotlin.math.*

/** The RN reference's PCHIP tracks and pure, 4.4-second scene in reference point space. */
internal object GlassPayMorphMotion {
    const val LOOP = 4.4
    const val TRAIL = 8
    const val COLLAPSE0 = 0.3
    const val COLLAPSE1 = 1.3
    const val DOCK = 1.34
    const val PULSE = 1.6
    const val PAID_HOLD = 2.2
    const val EXPAND0 = 2.8
    const val EXPAND1 = 3.9
    const val HERO_X = 207.0
    const val HERO_Y = 416.0
    private const val W = 2 * PI / LOOP

    data class Mods(
        val nu: Double = 1.0, val driftGain: Double = 1.0, val yaw: Double = 0.0,
        val press: Double = 1.0, val parallax: Double = 0.0,
        val aOff: DoubleArray = DoubleArray(4), val rOff: DoubleArray = DoubleArray(4),
    )
    data class Scene(
        val t: Double, val cx: Double, val cy: Double, val w: Double, val h: Double,
        val r: Double, val roll: Double, val turn: Double, val scale: Double,
        val a: DoubleArray, val rd: DoubleArray, val frostA: Double, val frostR: Double,
        val dock: Double, val tint: Double, val face: Double, val textName: Double,
        val textNumber: Double, val textChip: Double, val chipLines: Double,
        val rings: Double, val contactless: Double, val caption: Double, val pulse: Double,
        val flash: Double, val bright: Double, val sheen: Double, val glint: Double, val crawl: Double,
    )
    private class Track(keys: Array<DoubleArray>) {
        val t = keys.map { it[0] }.toDoubleArray()
        val v = keys.map { it[1] }.toDoubleArray()
        val m = DoubleArray(t.size)
        init {
            val d = DoubleArray(t.size - 1) { (v[it + 1] - v[it]) / (t[it + 1] - t[it]) }
            for (i in 1 until t.size - 1) {
                if (d[i - 1] * d[i] > 0) {
                    val h0 = t[i] - t[i - 1]
                    val h1 = t[i + 1] - t[i]
                    val w1 = 2 * h1 + h0
                    val w2 = h1 + 2 * h0
                    m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])
                }
            }
        }
        fun at(x: Double): Double {
            if (x <= t[0]) return v[0]
            if (x >= t.last()) return v.last()
            var i = 0
            while (i < t.size - 2 && x > t[i + 1]) i++
            val h = t[i + 1] - t[i]
            val u = (x - t[i]) / h
            val u2 = u * u
            val u3 = u2 * u
            return (2 * u3 - 3 * u2 + 1) * v[i] + (u3 - 2 * u2 + u) * h * m[i] +
                (-2 * u3 + 3 * u2) * v[i + 1] + (u3 - u2) * h * m[i + 1]
        }
    }
    private val A_KEYS = arrayOf(
        doubleArrayOf(0.0, -116.0, -47.0, 86.0),
        doubleArrayOf(0.3, -116.0, -47.0, 86.0),
        doubleArrayOf(0.4, -110.0, -51.0, 86.0),
        doubleArrayOf(0.5, -105.0, -55.0, 86.0),
        doubleArrayOf(0.57, -98.0, -59.0, 85.0),
        doubleArrayOf(0.63, -83.0, -63.0, 83.0),
        doubleArrayOf(0.7, -54.0, -66.0, 81.0),
        doubleArrayOf(0.73, -30.0, -64.0, 81.0),
        doubleArrayOf(0.77, 5.0, -60.0, 81.0),
        doubleArrayOf(0.8, 39.0, -49.0, 70.0),
        doubleArrayOf(0.83, 84.0, -58.0, 36.0),
        doubleArrayOf(0.87, 112.0, -52.0, 25.0),
        doubleArrayOf(0.9, 132.0, -45.0, 24.0),
        doubleArrayOf(0.93, 141.0, -35.0, 24.0),
        doubleArrayOf(0.97, 145.0, -24.0, 25.0),
        doubleArrayOf(1.0, 141.0, -15.0, 25.0),
        doubleArrayOf(1.03, 135.0, -10.0, 26.0),
        doubleArrayOf(1.07, 128.0, -7.0, 28.0),
        doubleArrayOf(1.1, 119.0, -5.0, 30.0),
        doubleArrayOf(1.13, 106.0, -4.0, 33.0),
        doubleArrayOf(1.17, 85.0, -3.0, 36.0),
        doubleArrayOf(1.23, 44.0, 0.0, 40.0),
        doubleArrayOf(1.3, 15.0, 2.0, 43.0),
        doubleArrayOf(1.4, 8.0, 2.0, 44.0),
        doubleArrayOf(2.8, 8.0, 2.0, 44.0),
        doubleArrayOf(2.97, 10.0, 3.0, 43.0),
        doubleArrayOf(3.03, 20.0, 7.0, 35.0),
        doubleArrayOf(3.07, 31.0, 12.0, 31.0),
        doubleArrayOf(3.13, 36.0, 26.0, 29.0),
        doubleArrayOf(3.17, 37.0, 38.0, 25.0),
        doubleArrayOf(3.2, 35.0, 52.0, 23.0),
        doubleArrayOf(3.23, 24.0, 70.0, 24.0),
        doubleArrayOf(3.27, 2.0, 88.0, 34.0),
        doubleArrayOf(3.3, -24.0, 93.0, 40.0),
        doubleArrayOf(3.33, -46.0, 95.0, 46.0),
        doubleArrayOf(3.37, -72.0, 88.0, 62.0),
        doubleArrayOf(3.4, -96.0, 70.0, 76.0),
        doubleArrayOf(3.43, -110.0, 53.0, 80.0),
        doubleArrayOf(3.47, -118.0, 35.0, 84.0),
        doubleArrayOf(3.53, -128.0, 8.0, 86.0),
        doubleArrayOf(3.57, -131.0, -4.0, 86.0),
        doubleArrayOf(3.63, -130.0, -23.0, 86.0),
        doubleArrayOf(3.7, -126.0, -34.0, 86.0),
        doubleArrayOf(3.8, -120.0, -44.0, 86.0),
        doubleArrayOf(3.9, -116.0, -47.0, 86.0),
        doubleArrayOf(4.4, -116.0, -47.0, 86.0),
    )
    private val R_KEYS = arrayOf(
        doubleArrayOf(0.0, 131.0, 72.0, 58.0),
        doubleArrayOf(0.3, 131.0, 72.0, 58.0),
        doubleArrayOf(0.4, 124.0, 80.0, 57.0),
        doubleArrayOf(0.5, 109.0, 91.0, 57.0),
        doubleArrayOf(0.57, 92.0, 99.0, 57.0),
        doubleArrayOf(0.63, 70.0, 108.0, 55.0),
        doubleArrayOf(0.7, 40.0, 116.0, 46.0),
        doubleArrayOf(0.73, 14.0, 124.0, 38.0),
        doubleArrayOf(0.77, -22.0, 123.0, 30.0),
        doubleArrayOf(0.8, -55.0, 118.0, 27.0),
        doubleArrayOf(0.83, -98.0, 101.0, 25.0),
        doubleArrayOf(0.87, -124.0, 86.0, 25.0),
        doubleArrayOf(0.9, -141.0, 69.0, 25.0),
        doubleArrayOf(0.93, -150.0, 54.0, 26.0),
        doubleArrayOf(0.97, -152.0, 41.0, 28.0),
        doubleArrayOf(1.0, -151.0, 31.0, 30.0),
        doubleArrayOf(1.03, -148.0, 23.0, 32.0),
        doubleArrayOf(1.07, -144.0, 17.0, 35.0),
        doubleArrayOf(1.1, -136.0, 11.0, 37.0),
        doubleArrayOf(1.13, -122.0, 4.0, 39.0),
        doubleArrayOf(1.17, -106.0, -1.0, 39.0),
        doubleArrayOf(1.23, -76.0, -7.0, 36.0),
        doubleArrayOf(1.3, -52.0, -12.0, 32.0),
        doubleArrayOf(1.4, -40.0, -14.0, 30.0),
        doubleArrayOf(2.8, -40.0, -14.0, 30.0),
        doubleArrayOf(2.93, -41.0, -16.0, 30.0),
        doubleArrayOf(3.0, -42.0, -24.0, 27.0),
        doubleArrayOf(3.03, -44.0, -34.0, 25.0),
        doubleArrayOf(3.07, -36.0, -56.0, 24.0),
        doubleArrayOf(3.1, -18.0, -72.0, 26.0),
        doubleArrayOf(3.13, 8.0, -84.0, 27.0),
        doubleArrayOf(3.17, 36.0, -90.0, 27.0),
        doubleArrayOf(3.2, 62.0, -89.0, 27.0),
        doubleArrayOf(3.23, 92.0, -82.0, 28.0),
        doubleArrayOf(3.27, 130.0, -64.0, 38.0),
        doubleArrayOf(3.3, 152.0, -40.0, 46.0),
        doubleArrayOf(3.33, 160.0, -18.0, 46.0),
        doubleArrayOf(3.37, 162.0, -5.0, 50.0),
        doubleArrayOf(3.4, 164.0, 10.0, 52.0),
        doubleArrayOf(3.47, 160.0, 34.0, 52.0),
        doubleArrayOf(3.53, 153.0, 49.0, 56.0),
        doubleArrayOf(3.57, 150.0, 56.0, 58.0),
        doubleArrayOf(3.67, 142.0, 65.0, 58.0),
        doubleArrayOf(3.8, 134.0, 69.0, 58.0),
        doubleArrayOf(3.9, 131.0, 72.0, 58.0),
        doubleArrayOf(4.4, 131.0, 72.0, 58.0),
    )
    private val A_SPAN = Track(arrayOf(
        doubleArrayOf(0.0, 0.06),
        doubleArrayOf(1.0, 0.06),
        doubleArrayOf(1.12, 0.02),
        doubleArrayOf(2.9, 0.02),
        doubleArrayOf(2.98, 0.06),
        doubleArrayOf(3.1, 0.11),
        doubleArrayOf(3.28, 0.14),
        doubleArrayOf(3.47, 0.06),
        doubleArrayOf(4.4, 0.06),
    ))
    private val R_SPAN = Track(arrayOf(
        doubleArrayOf(0.0, 0.075),
        doubleArrayOf(1.0, 0.075),
        doubleArrayOf(1.12, 0.025),
        doubleArrayOf(2.88, 0.025),
        doubleArrayOf(2.95, 0.075),
        doubleArrayOf(3.06, 0.16),
        doubleArrayOf(3.2, 0.16),
        doubleArrayOf(3.32, 0.07),
        doubleArrayOf(4.4, 0.075),
    ))
    private val KT = doubleArrayOf(0.0, 0.6, 0.77, 0.83, 0.9, 1.0, 1.1, 1.17, 1.23, 1.3, 2.8, 3.0, 3.1, 3.17, 3.23, 3.3, 3.37, 3.47, 3.57, 3.67, 3.9, 4.4)
    private fun zip(vararg values: Double) = Track(Array(KT.size) { doubleArrayOf(KT[it], values[it]) })
    private fun heads(keys: Array<DoubleArray>, span: Track, channel: Int): Track {
        val shifted = keys.map { doubleArrayOf(it[0] - 0.25 * span.at(it[0]), it[1 + channel]) }.toTypedArray()
        for (i in 1 until shifted.size) {
            if (shifted[i][0] <= shifted[i - 1][0]) shifted[i][0] = shifted[i - 1][0] + 1e-3
        }
        return Track(shifted)
    }
    private fun radii(keys: Array<DoubleArray>) = Track(keys.map { doubleArrayOf(it[0], it[3]) }.toTypedArray())
    private val cardX = zip(0.0, 0.0, 0.0, 0.0, 0.0, -1.0, -2.0, -4.0, -5.0, -6.0, -6.0, -6.0, -6.0, -7.0, -10.0, -12.0, 6.0, 7.0, 5.0, 2.0, 0.0, 0.0)
    private val cardW = zip(282.0, 282.0, 265.0, 233.0, 218.0, 208.0, 206.0, 204.0, 196.0, 192.0, 192.0, 192.0, 196.0, 200.0, 205.0, 240.0, 250.0, 262.0, 280.0, 282.0, 282.0, 282.0)
    private val cardH = zip(168.0, 168.0, 160.0, 141.0, 132.0, 126.0, 124.0, 123.0, 123.0, 123.0, 123.0, 123.0, 123.0, 125.0, 135.0, 143.0, 150.0, 158.0, 167.0, 168.0, 168.0, 168.0)
    private val cardR = zip(15.0, 15.0, 16.0, 18.0, 20.0, 23.0, 28.0, 45.0, 60.0, 61.5, 61.5, 61.5, 61.0, 58.0, 44.0, 28.0, 18.0, 15.0, 15.0, 15.0, 15.0, 15.0)
    private val roll = zip(-3.0, -3.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -0.5, -1.5, -2.5, -3.0, -3.0, -3.0)
    private val aX = heads(A_KEYS, A_SPAN, 0)
    private val aY = heads(A_KEYS, A_SPAN, 1)
    private val rX = heads(R_KEYS, R_SPAN, 0)
    private val rY = heads(R_KEYS, R_SPAN, 1)
    private val aR = radii(A_KEYS)
    private val rR = radii(R_KEYS)
    private val frostA = Track(arrayOf(
        doubleArrayOf(0.0, 1.0),
        doubleArrayOf(0.94, 1.0),
        doubleArrayOf(1.0, 0.0),
        doubleArrayOf(3.19, 0.0),
        doubleArrayOf(3.27, 1.0),
        doubleArrayOf(4.4, 1.0),
    ))
    private val frostR = Track(arrayOf(
        doubleArrayOf(0.0, 1.0),
        doubleArrayOf(0.94, 1.0),
        doubleArrayOf(1.0, 0.0),
        doubleArrayOf(3.4, 0.0),
        doubleArrayOf(3.49, 1.0),
        doubleArrayOf(4.4, 1.0),
    ))
    private val turn = Track(arrayOf(
        doubleArrayOf(2.8, 180.0),
        doubleArrayOf(3.2, 180.0),
        doubleArrayOf(3.23, 162.0),
        doubleArrayOf(3.27, 140.0),
        doubleArrayOf(3.3, 114.0),
        doubleArrayOf(3.315, 90.0),
        doubleArrayOf(3.33, 74.0),
        doubleArrayOf(3.37, 55.0),
        doubleArrayOf(3.4, 47.0),
        doubleArrayOf(3.47, 33.0),
        doubleArrayOf(3.57, 21.0),
        doubleArrayOf(3.67, 12.0),
        doubleArrayOf(3.77, 5.0),
        doubleArrayOf(3.9, 0.0),
        doubleArrayOf(4.4, 0.0),
    ))

    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun smooth(a: Double, b: Double, x: Double): Double {
        val k = clamp01((x - a) / (b - a))
        return k * k * (3 - 2 * k)
    }
    fun wrap(t: Double): Double {
        val m = t % LOOP
        return if (m < 0) m + LOOP else m
    }
    fun springBump(u: Double, response: Double, damping: Double): Double {
        if (u <= 0) return 0.0
        val w = 2 * PI / response
        val wd = w * sqrt(1 - damping * damping)
        val tp = atan(wd / (damping * w)) / wd
        val peak = exp(-damping * w * tp) * sin(wd * tp)
        return exp(-damping * w * u) * sin(wd * u) / peak
    }
    private fun restWeight(t: Double) = 1 - smooth(0.12, 0.34, t) + smooth(3.6, 3.9, t)
    private fun dockJelly(t: Double): Double {
        if (t < DOCK || t > EXPAND0) return 0.0
        return 3 * springBump(t - DOCK, 0.4, 0.55) * (1 - smooth(2.5, 2.8, t))
    }
    private fun lightAt(tr: Double, amber: Boolean, gain: Double, sign: Double, ux: Double, uy: Double): DoubleArray {
        val t = wrap(tr)
        val dx = if (amber) 4 * sin(W * t) else 5 * sin(2 * W * t)
        val dy = if (amber) 3 * sin(2 * W * t) else 4 * (1 - cos(2 * W * t))
        val k = restWeight(t) * gain
        val j = dockJelly(t) * sign
        return doubleArrayOf(
            HERO_X + (if (amber) aX else rX).at(t) + dx * k + j * ux,
            HERO_Y + (if (amber) aY else rY).at(t) + dy * k + j * uy,
        )
    }
    fun sceneAt(tau: Double, m: Mods = Mods()): Scene {
        val t = wrap(tau)
        val rw = restWeight(t) * m.driftGain
        val float = sin(W * t)
        val turn = (if (t < COLLAPSE1) 0.0 else if (t < EXPAND0) 180.0 else turn.at(t)) + m.yaw
        val pulse = if (t >= PULSE && t < EXPAND0) t - PULSE else -1.0
        val breath = if (pulse >= 0) 0.04 * springBump(pulse, 0.4, 0.6) else 0.0
        val dl = hypot(48.0, 16.0)
        val ux = 48 / dl
        val uy = 16 / dl
        val a = DoubleArray(TRAIL * 3)
        val rd = DoubleArray(TRAIL * 3)
        val ar = aR.at(t)
        val rr = rR.at(t)
        val aSpan = A_SPAN.at(t) * m.nu
        val rSpan = R_SPAN.at(t) * m.nu
        for (k in 0 until TRAIL) {
            val f = k.toDouble() / (TRAIL - 1)
            val taper = 1 - 0.3 * f
            val pa = lightAt(t - f * aSpan, true, m.driftGain, -1.0, ux, uy)
            val pr = lightAt(t - f * rSpan, false, m.driftGain, 1.0, ux, uy)
            val ta = f * 0.056
            a[k * 3] = pa[0] + m.parallax + m.aOff[0] - m.aOff[2] * ta
            a[k * 3 + 1] = pa[1] + m.aOff[1] - m.aOff[3] * ta
            a[k * 3 + 2] = ar * taper
            rd[k * 3] = pr[0] + m.parallax * 1.3 + m.rOff[0] - m.rOff[2] * ta
            rd[k * 3 + 1] = pr[1] + m.rOff[1] - m.rOff[3] * ta
            rd[k * 3 + 2] = rr * taper
        }
        var face = 1.0
        var textName = 1.0
        var textNumber = 1.0
        var textChip = 1.0
        var chipLines = 1.0
        var rings = 1.0
        var contactless = 1.0
        if (t >= COLLAPSE0 && t < EXPAND0) {
            textName = 1 - seg(t, 1.06, 1.15)
            contactless = 1 - seg(t, 1.08, 1.17)
            textChip = 1 - seg(t, 1.1, 1.19)
            textNumber = 1 - seg(t, 1.14, 1.25)
        } else if (t >= EXPAND0 && t < EXPAND1) {
            face = if (turn < 90) clamp01((cos(turn * PI / 180) - 0.05) / 0.25) else 0.0
            rings = easeInOut(seg(t, 3.37, 3.56))
            chipLines = easeInOut(seg(t, 3.5, 3.72))
            contactless = easeOut(seg(t, 3.6, 3.76))
        }
        if (m.yaw != 0.0 && t < COLLAPSE0) face = clamp01((cos(turn * PI / 180) - 0.15) / 0.4)
        val caption = if (t < EXPAND0) easeOut(seg(t, 1.3, 1.5)) else 1 - easeOut(seg(t, 3.57, 3.77))
        val dock = if (t < EXPAND0) smooth(1.0, 1.3, t) else 1 - smooth(3.0, 3.45, t)
        val morph = if (t < EXPAND0) smooth(0.6, 1.3, t) else 1 - smooth(3.1, 3.6, t)
        return Scene(
            t, HERO_X + cardX.at(t), HERO_Y + 2 * float * rw,
            cardW.at(t), cardH.at(t), cardR.at(t), roll.at(t) + 0.5 * sin(W * t) * rw,
            turn, m.press * (1 + breath), a, rd, frostA.at(t), frostR.at(t), dock,
            0.062 + 0.045 * morph, face, textName, textNumber, textChip, chipLines, rings,
            contactless, caption, pulse,
            if (pulse >= 0) exp(-pulse / 0.09) * seg(pulse, -0.001, 0.03) else 0.0,
            if (pulse >= 0) exp(-pulse / 0.08) else 0.0,
            if (t >= 3.37 && t <= 3.67) (t - 3.37) / 0.3 else -1.0,
            if (t >= 3.62 && t <= 3.8) sin(PI * seg(t, 3.62, 3.8)) else 0.0, t / LOOP,
        )
    }
    /** Row-major local top-left → screen homography, identical to the RN matrix. */
    fun cardMatrix(s: Scene): DoubleArray {
        val th = s.turn * PI / 180
        val ct = cos(th)
        val st = sin(th)
        val ro = s.roll * PI / 180
        val cr = cos(ro)
        val sr = sin(ro)
        val g = st / 900
        val a = cr * ct + s.cx * g
        val b = -sr
        val d = sr * ct + s.cy * g
        val e = cr
        val k = s.scale
        val hx = -k * s.w / 2
        val hy = -k * s.h / 2
        return doubleArrayOf(a * k, b * k, a * hx + b * hy + s.cx,
            d * k, e * k, d * hx + e * hy + s.cy, g * k, 0.0, g * hx + 1)
    }
    /** Screen → centred card-local; centring is folded into the inverse. */
    fun cardInverse(s: Scene): DoubleArray {
        val th = s.turn * PI / 180
        val cr = cos(s.roll * PI / 180)
        val sr = sin(s.roll * PI / 180)
        val g = sin(th) / 900
        val k = s.scale
        val m00 = (cr * cos(th) + s.cx * g) * k
        val m01 = -sr * k
        val m02 = s.cx
        val m10 = (sr * cos(th) + s.cy * g) * k
        val m11 = cr * k
        val m12 = s.cy
        val m20 = g * k
        val c00 = m11
        val c01 = -m01
        val c02 = m01 * m12 - m02 * m11
        val c10 = m12 * m20 - m10
        val c11 = m00 - m02 * m20
        val c12 = m02 * m10 - m00 * m12
        val c20 = -m11 * m20
        val c21 = m01 * m20
        val c22 = m00 * m11 - m01 * m10
        val det = m00 * c00 + m01 * c10 + m02 * c20
        // At exactly edge-on the inverse tends to infinity and the shader has zero slab coverage.
        val inv = 1 / det
        return doubleArrayOf(c00 * inv, c01 * inv, c02 * inv, c10 * inv, c11 * inv,
            c12 * inv, c20 * inv, c21 * inv, c22 * inv)
    }
    fun progressOf(tau: Double): Double {
        val t = wrap(tau)
        if (t < COLLAPSE0 || t >= EXPAND1) return 0.0
        if (t <= COLLAPSE1) return (t - COLLAPSE0) / (COLLAPSE1 - COLLAPSE0)
        if (t < EXPAND0) return 1.0
        return 1 - (t - EXPAND0) / (EXPAND1 - EXPAND0)
    }
}
