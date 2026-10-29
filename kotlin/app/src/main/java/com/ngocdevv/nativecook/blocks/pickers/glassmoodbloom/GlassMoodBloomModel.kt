package com.ngocdevv.nativecook.blocks.pickers.glassmoodbloom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Glass Mood Bloom — the numbers (a port of GlassMoodBloomModel.swift): the seven moods, the loop's value V(t),
 the layer parameters and the touch driver. Everything the screen draws is a function of the mood value
 v ∈ [-1, 1] (−1 Very Unpleasant … 0 Neutral … 1 Very Pleasant), and v is a function of time: the loop's timeline,
 or the finger's samples, a detent spring and the ease back to Neutral after a touch.
*/

internal class GmbRgb(val r: Double, val g: Double, val b: Double) {
    fun mix(o: GmbRgb, t: Double) = GmbRgb(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t)

    companion object {
        fun hex(h: Int) = GmbRgb(((h shr 16) and 0xFF) / 255.0, ((h shr 8) and 0xFF) / 255.0, (h and 0xFF) / 255.0)
        val WHITE = hex(0xFFFFFF)
    }
}

internal class GmbMood(
    val name: String,
    val rv: Double, // valley radius / tip radius of the lobed outline
    val p: Double, // petal profile exponent (< 1 round lobes, > 1 thin spikes)
    val q: Double, // 0 lobed outline, 1 polygon outline
    val e: Double, // polygon side exponent (0 circle, 1 straight sides, > 1 concave star sides)
    val k: Double, // polygon corner sharpness (1 sharp, lower rounder)
    val twist: Double, // degrees the outer plate turns against the others
    val size: Double,
    val deep: GmbRgb, val mid: GmbRgb, val pale: GmbRgb,
    val bgTop: GmbRgb, val bgBottom: GmbRgb, val button: GmbRgb, val label: GmbRgb,
)

/** The mood's parameters at v, interpolated between the two neighbouring anchors. */
internal class GmbLook(
    val n: Double, val rv: Double, val p: Double, val q: Double, val e: Double, val k: Double,
    val twist: Double, val size: Double,
    val deep: GmbRgb, val mid: GmbRgb, val pale: GmbRgb,
    val bgTop: GmbRgb, val bgBottom: GmbRgb, val button: GmbRgb, val label: GmbRgb,
)

internal object GmbModel {
    const val LOOP = 10.0

    private fun h(x: Int) = GmbRgb.hex(x)

    val MOODS = listOf(
        GmbMood("Very Unpleasant", 0.5, 1.6, 0.5, 7.0, 0.975, 12.0, 1.04,
            h(0x7B45E6), h(0xAE92F2), h(0xECE6FC), h(0xD6CBEE), h(0xB5A3DE), h(0x2C2382), h(0x2B1F66)),
        GmbMood("Unpleasant", 0.65, 1.2, 0.6, 4.0, 0.94, 8.0, 1.0,
            h(0x4E6AEE), h(0x95A6F4), h(0xE4E8FC), h(0xD3D6F2), h(0xB7BCE8), h(0x1B3DBE), h(0x1E2A6E)),
        GmbMood("Slightly Unpleasant", 0.87, 0.8, 0.0, 0.0, 0.9, 4.0, 0.97,
            h(0x3B8EF0), h(0x8CC0F7), h(0xE2EEFC), h(0xD7E0F2), h(0xBCCBEA), h(0x0E58C8), h(0x18346A)),
        GmbMood("Neutral", 1.0, 1.0, 0.0, 0.0, 0.9, 0.0, 0.95,
            h(0x47B4E4), h(0x9DD7EF), h(0xE9F6FB), h(0xE1E8EC), h(0xC8D6DD), h(0x3591B8), h(0x22404E)),
        GmbMood("Slightly Pleasant", 1.0, 1.0, 1.0, 1.0, 0.88, 3.0, 0.98,
            h(0x5CCB24), h(0xA7E36C), h(0xE8F6D2), h(0xD6E8BE), h(0xB3D595), h(0x2C8A0B), h(0x27461A)),
        GmbMood("Pleasant", 1.0, 1.0, 1.0, 3.2, 0.85, 5.0, 1.0,
            h(0xF0BE16), h(0xF8DC66), h(0xFFF4C8), h(0xF1E5B6), h(0xE6CB82), h(0xD08F08), h(0x4C3A0C)),
        GmbMood("Very Pleasant", 0.55, 0.45, 0.0, 0.0, 0.85, 10.0, 1.04,
            h(0xFF6A2A), h(0xFFAA6A), h(0xFFE8D2), h(0xFCE3CA), h(0xF6C298), h(0xFA6A2C), h(0x5A2A12)),
    )

    fun look(v: Double): GmbLook {
        val x = (v.coerceIn(-1.0, 1.0) + 1) * 3
        val i = minOf(floor(x).toInt(), 5)
        val f = x - i
        val a = MOODS[i]
        val b = MOODS[i + 1]
        fun l(p: Double, q: Double) = p + (q - p) * f
        return GmbLook(
            n = if (v > 0) 5.0 else 8.0, rv = l(a.rv, b.rv), p = l(a.p, b.p), q = l(a.q, b.q), e = l(a.e, b.e),
            k = l(a.k, b.k), twist = l(a.twist, b.twist), size = l(a.size, b.size),
            deep = a.deep.mix(b.deep, f), mid = a.mid.mix(b.mid, f), pale = a.pale.mix(b.pale, f),
            bgTop = a.bgTop.mix(b.bgTop, f), bgBottom = a.bgBottom.mix(b.bgBottom, f),
            button = a.button.mix(b.button, f), label = a.label.mix(b.label, f),
        )
    }

    /** 0 … 6 into MOODS. */
    fun bucket(v: Double) = (v.coerceIn(-1.0, 1.0) * 3).roundToInt() + 3

    // Layers

    val LAYER_SCALE = doubleArrayOf(1.00, 0.91, 0.64, 0.52, 0.40, 0.27)
    val LAYER_ALPHA = doubleArrayOf(0.16, 0.22, 0.42, 0.55, 0.68, 0.80)
    const val LAYER_LAG = 0.045

    fun smooth(a: Double, b: Double, x: Double): Double {
        val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    fun layerColor(l: GmbLook, k: Double): GmbRgb = l.pale.mix(l.mid, smooth(0.0, 0.5, k)).mix(l.deep, smooth(0.4, 1.0, k))

    /**
     * Radius (tip = 1) at angle a from a petal tip: a mix of a lobed outline (rv + (1 − rv) · ((1 + cos na) / 2)^p)
     * and a rounded polygon / star outline ((cos ψt / cos ψ)^e, ψ = acos(−k cos na) / n).
     */
    fun radius(a: Double, l: GmbLook): Double {
        val c = cos(l.n * a)
        val lobe = l.rv + (1 - l.rv) * ((1 + c) / 2).coerceAtLeast(0.0).pow(l.p)
        val psiT = acos(-l.k) / l.n
        val psi = acos(-l.k * c) / l.n
        val poly = (cos(psiT) / cos(psi)).pow(l.e)
        return lobe + (poly - lobe) * l.q
    }

    /** The smallest radius of the outline (tip = 1). */
    fun valley(l: GmbLook): Double {
        val poly = cos(acos(-l.k) / l.n).pow(l.e)
        return l.rv + (poly - l.rv) * l.q
    }

    // Loop timeline

    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    fun easeOut(x: Double): Double = 1 - (1 - x.coerceIn(0.0, 1.0)).pow(3)

    /** start, end, from, to; between moves the value holds. */
    private val MOVES = arrayOf(
        doubleArrayOf(1.20, 1.75, 0.0, 1.0 / 3), doubleArrayOf(2.15, 2.70, 1.0 / 3, 2.0 / 3),
        doubleArrayOf(3.10, 3.65, 2.0 / 3, 1.0), doubleArrayOf(4.50, 5.30, 1.0, 0.0),
        doubleArrayOf(5.50, 6.00, 0.0, -1.0 / 3), doubleArrayOf(6.30, 6.80, -1.0 / 3, -2.0 / 3),
        doubleArrayOf(7.10, 7.60, -2.0 / 3, -1.0), doubleArrayOf(8.40, 9.30, -1.0, 0.0),
    )

    fun loopValue(time: Double): Double {
        var t = time % LOOP
        if (t < 0) t += LOOP
        var v = 0.0
        for (m in MOVES) {
            if (t < m[0]) return v
            if (t < m[1]) return m[2] + (m[3] - m[2]) * easeInOut((t - m[0]) / (m[1] - m[0]))
            v = m[3]
        }
        return v
    }

    fun breathe(t: Double, i: Int) = sin(2 * PI * 3 * t / LOOP + 0.7 * i) - sin(0.7 * i)

    fun wobble(t: Double, i: Int) = sin(2 * PI * t / LOOP + 0.9 * i) - sin(0.9 * i)

    // Spring (release to the nearest mood)

    private const val OMEGA = 2 * PI / 0.42
    private const val ZETA = 0.8

    fun spring(dt: Double, x0: Double, v0: Double, target: Double): Double {
        val a = x0 - target
        val wd = OMEGA * sqrt(1 - ZETA * ZETA)
        val b = (v0 + ZETA * OMEGA * a) / wd
        return target + exp(-ZETA * OMEGA * dt) * (a * cos(wd * dt) + b * sin(wd * dt))
    }

    const val RETURN_DELAY = 2.0
    const val RETURN_TIME = 0.9
}

/** Holds what a touch changed; every value is still read as a function of time. */
internal class GmbDriver {
    private val m = GmbModel
    private var epoch: Double? = null
    private val ts = ArrayList<Double>()
    private val vs = ArrayList<Double>()
    var touching = false
        private set
    private var touchStart = Double.POSITIVE_INFINITY
    private var hasRelease = false
    private var relT = 0.0
    private var relX = 0.0
    private var relVel = 0.0
    private var relTarget = 0.0
    private var rest = 0.0
    var auto = true
    var reduced = false

    /** Call once per frame with the frame's time, before reading values. */
    fun begin(now: Double) {
        if (epoch == null) epoch = now
        if (auto && !touching && hasRelease && now > relT + m.RETURN_DELAY + m.RETURN_TIME + 0.5) {
            epoch = relT + m.RETURN_DELAY + m.RETURN_TIME
            hasRelease = false
            ts.clear()
            vs.clear()
            touchStart = Double.POSITIVE_INFINITY
        }
    }

    /** True while something still moves after a touch (the frame loop keeps running). */
    fun busy(now: Double) = touching || (hasRelease && now < relT + m.RETURN_DELAY + m.RETURN_TIME + 1.0)

    fun loopTime(t: Double): Double {
        val e = epoch ?: t
        if (auto && hasRelease && t >= relT + m.RETURN_DELAY + m.RETURN_TIME) return t - (relT + m.RETURN_DELAY + m.RETURN_TIME)
        return t - e
    }

    fun breatheWeight(t: Double): Double {
        if (!auto || reduced) return 0.0
        if (hasRelease && !touching) {
            val dt = t - relT - m.RETURN_DELAY
            if (dt >= 0 && dt < m.RETURN_TIME) return 1 - m.easeInOut(dt / m.RETURN_TIME)
        }
        return 1.0
    }

    fun value(t: Double): Double {
        if (t < touchStart || ts.isEmpty()) {
            if (!auto || reduced) return rest
            return m.loopValue(t - (epoch ?: t))
        }
        if (touching || !hasRelease || t < relT) {
            if (t <= ts[0]) return vs[0]
            for (k in ts.size - 1 downTo 0) {
                if (ts[k] <= t) {
                    if (k == ts.size - 1) return vs[k]
                    val f = (t - ts[k]) / maxOf(ts[k + 1] - ts[k], 1e-6)
                    return vs[k] + (vs[k + 1] - vs[k]) * f
                }
            }
            return vs[0]
        }
        val dt = t - relT
        if (reduced) return relTarget
        if (!auto || dt < m.RETURN_DELAY) return m.spring(dt, relX, relVel, relTarget)
        if (dt < m.RETURN_DELAY + m.RETURN_TIME) {
            val a = m.spring(m.RETURN_DELAY, relX, relVel, relTarget)
            return a * (1 - m.easeInOut((dt - m.RETURN_DELAY) / m.RETURN_TIME))
        }
        return m.loopValue(dt - m.RETURN_DELAY - m.RETURN_TIME)
    }

    /** The finger went down: keep the last 0.3 s of the value as history so the lagging layers stay continuous. */
    fun touchDown(now: Double): Double {
        val current = value(now)
        val preT = DoubleArray(9) { now - 0.3 + it * 0.0375 }
        val preV = DoubleArray(9) { value(preT[it]) }
        ts.clear()
        vs.clear()
        for (i in 0 until 9) {
            ts.add(preT[i])
            vs.add(preV[i])
        }
        touchStart = preT[0]
        touching = true
        hasRelease = false
        return current
    }

    fun touchMove(now: Double, v: Double) {
        ts.add(now)
        vs.add(v.coerceIn(-1.0, 1.0))
        if (ts.size > 240) {
            ts.subList(0, ts.size - 240).clear()
            vs.subList(0, vs.size - 240).clear()
        }
    }

    fun touchUp(now: Double) {
        touching = false
        val last = vs.lastOrNull() ?: 0.0
        var vel = 0.0
        var k = ts.size - 1
        while (k >= 0 && ts[k] > now - 0.06) k--
        if (k >= 0 && ts.isNotEmpty() && ts.last() > ts[k]) vel = (vs.last() - vs[k]) / maxOf(now - ts[k], 0.016)
        vel = vel.coerceIn(-6.0, 6.0)
        val target = (m.bucket(last) - 3) / 3.0
        ts.add(now)
        vs.add(last)
        hasRelease = true
        relT = now
        relX = last
        relVel = if (reduced) 0.0 else vel
        relTarget = target
        if (!auto) rest = target
    }
}

/** Everything a frame needs, from time. */
internal class GmbFrame(
    val v: Double,
    val layerV: DoubleArray,
    val swell: DoubleArray,
    val loopT: Double,
    val breath: Double,
    val label: Int,
    val labelPrev: Int,
    val nowAlpha: Double,
    val nowDy: Double,
    val prevAlpha: Double,
    val prevDy: Double,
) {
    companion object {
        fun make(d: GmbDriver, now: Double, reduced: Boolean): GmbFrame {
            val m = GmbModel
            d.begin(now)
            val v = d.value(now)
            val layerV = DoubleArray(6) { i -> if (reduced) v else d.value(now - (5 - i) * m.LAYER_LAG) }
            // Plates swell a little while they change and settle when they arrive
            val swell = DoubleArray(6) { i ->
                if (reduced) 0.0 else {
                    val t = now - (5 - i) * m.LAYER_LAG
                    minOf(0.06, 0.5 * abs(d.value(t) - d.value(t - 0.1)))
                }
            }
            val b = m.bucket(v)
            var change: Double? = null
            var prev = b
            for (k in 1..18) {
                val t = now - k / 60.0
                val bb = m.bucket(d.value(t))
                if (bb != b) {
                    change = t + 1.0 / 120
                    prev = bb
                    break
                }
            }
            var pa = 0.0
            var pd = 0.0
            var na = 1.0
            var nd = 0.0
            if (change != null) {
                val e = now - change
                val out = (e / 0.14).coerceIn(0.0, 1.0)
                pa = 1 - out * out
                pd = -10 * out * out
                val inn = m.easeOut((e - 0.06) / 0.24)
                na = inn
                nd = 10 * (1 - inn)
            }
            return GmbFrame(v, layerV, swell, d.loopTime(now), d.breatheWeight(now), b, prev, na, nd, pa, pd)
        }
    }
}
