package com.ngocdevv.nativecook.blocks.navigation.greatwavemorning

import kotlin.math.*

/** Analytic RN timeline. Times are seconds; scroll and velocity are in the pin's pixels. */
internal object GreatWaveMorningMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val PULL = 150.0
    const val COLLAPSE = 340.0
    const val MAX_SCROLL = 430.0

    object T {
        val pull = doubleArrayOf(1.2, 1.9)
        const val release = 2.1
        val collapse = doubleArrayOf(3.0, 4.1)
        val unfold = doubleArrayOf(5.6, 6.8)
    }

    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))

    fun mix(a: Number, b: Number, p: Double) = a.toDouble() + (b.toDouble() - a.toDouble()) * p

    fun smooth(x: Double): Double {
        val p = clamp01(x)
        return p * p * (3 - 2 * p)
    }

    fun easeOut(x: Double) = 1 - (1 - clamp01(x)).pow(3)

    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p < 0.5) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2
    }

    fun spring(tau: Double, response: Double, damping: Double): Double {
        if (tau <= 0) return 0.0
        val w = 2 * PI / response
        val zw = damping * w
        val wd = w * sqrt(1 - damping * damping)
        return 1 - exp(-zw * tau) * (cos(wd * tau) + zw / wd * sin(wd * tau))
    }

    fun rubber(d: Double) = (1 - 1 / (d * 0.55 / 900 + 1)) * 900

    fun loopScroll(t: Double): Double {
        if (t < T.release) return -PULL * easeOut(seg(t, T.pull[0], T.pull[1]))
        if (t < T.collapse[0]) return -PULL * (1 - spring(t - T.release, 0.55, 0.62))
        if (t < T.unfold[0]) return (COLLAPSE + 40) * easeInOut(seg(t, T.collapse[0], T.collapse[1]))
        return (COLLAPSE + 40) * (1 - easeInOut(seg(t, T.unfold[0], T.unfold[1])))
    }

    fun frameAt(t: Double) = GreatWaveMorningFrame(t, loopScroll(t), if (t >= T.release && t < T.release + 1.6) t - T.release else -1.0)
}
