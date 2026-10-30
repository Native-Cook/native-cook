package com.ngocdevv.nativecook.blocks.modals.spilledcoffeeerror

import kotlin.math.*

/** Analytic timeline, in seconds. These equations are shared by autoplay and touch retries. */
internal object SpilledCoffeeErrorMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    object T {
        const val press = 1.0
        val rewind = doubleArrayOf(1.15, 2.35)
        const val rise = 1.9
        val lid = doubleArrayOf(2.35, 2.85)
        val text = doubleArrayOf(1.2, 1.5)
        val steam = doubleArrayOf(3.0, 5.6)
        const val fail = 5.65
        const val land = 6.2
        val lidOff = doubleArrayOf(6.15, 6.7)
        val spill = doubleArrayOf(6.2, 7.4)
        val textBack = doubleArrayOf(5.8, 6.1)
    }

    data class Pose(
    var amb: Double, var theta: Double, var spill: Double, var pour: Double,
    var lid: Double, var lidHop: Double, var text: Double, var steam: Double,
    var dots: Double, var rewind: Double, var press: Double, var splash: Double, var drip: Double,
    )
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p
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
        val w = 2 * Math.PI / response
        val zw = damping * w
        val wd = w * sqrt(1 - damping * damping)
        return 1 - exp(-zw * tau) * (cos(wd * tau) + zw / wd * sin(wd * tau))
    }

    fun retryPose(tau: Double, th0: Double, s0: Double, lid0: Double, amb: Double): Pose {
        val t = T.press + tau
        val rw = seg(t, T.rewind[0], T.rewind[1])
        val theta: Double
        var spill = s0 * (1 - easeInOut(rw))
        var lid = lid0 * (1 - easeInOut(seg(t, T.lid[0], T.lid[1])))
        var pour = 0.0
        var splash = -1.0
        if (t < T.fail) theta = th0 * (1 - spring(t - T.rise, 0.55, 0.5))
        else if (t < T.land) {
            val p = seg(t, T.fail, T.land)
            theta = 90 * p * p
        }
        else {
            val q = t - T.land
            theta = 90 - 9 * exp(-q * 7) * abs(sin(q * 14))
            lid = easeOut(seg(t, T.lidOff[0], T.lidOff[1]))
            spill = easeOut(seg(t, T.spill[0], T.spill[1]))
            pour = 1 - smooth(seg(t, T.spill[1] - 0.5, T.spill[1]))
            splash = t - T.land
        }

        if (t >= T.lidOff[0] && t < T.land) lid = easeOut(seg(t, T.lidOff[0], T.lidOff[1]))
        val text = smooth(seg(t, T.text[0], T.text[1])) * (1 - smooth(seg(t, T.textBack[0], T.textBack[1])))
        val steam = smooth(seg(t, T.steam[0], T.steam[0] + 0.6)) * (1 - smooth(seg(t, T.steam[1] - 0.4, T.steam[1])))
        val dots = smooth(seg(t, 1.25, 1.45)) * (1 - smooth(seg(t, 5.7, 5.9)))
        val press = max(0.0, 1 - abs(t - T.press - 0.06) / 0.12)
        return Pose(amb, theta, spill, pour, lid, -1.0, text, steam, dots, if (rw > 0 && rw < 1) sin(Math.PI * rw) else 0.0, press, splash, -1.0)
    }

    fun poseAt(t: Double): Pose {
        if (t < T.press) {
            val p = retryPose(LOOP - T.press + t, 90.0, 1.0, 1.0, t)
            p.drip = if (t >= 0.35) t - 0.35 else -1.0
            p.pour = 0.0
            p.splash = -1.0
            return p
        }

        val p = retryPose(t - T.press, 90.0, 1.0, 1.0, t)
        if (t >= 7.9) p.drip = t - 7.9
        return p
    }
}
