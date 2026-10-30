package com.ngocdevv.nativecook.blocks.cards.sunuvbuddy

import kotlin.math.*

/** Pure RN timeline and analytic bar springs; all times are seconds. */
internal object SunUvBuddyMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val REST_DAY = 2
    const val REST_HOUR = 3.0
    object T {
        val toEvening = doubleArrayOf(1.0, 2.4)
        val toNoon = doubleArrayOf(3.0, 3.9)
        const val today = 4.4
        val hourUp = doubleArrayOf(4.45, 5.0)
        const val thu = 6.6
        val hourDown = doubleArrayOf(6.6, 7.15)
    }
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p
    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p < 0.5) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2
    }
    fun smooth(x: Double): Double { val p = clamp01(x); return p * p * (3 - 2 * p) }
    fun spring(tau: Double, response: Double, damping: Double): Double {
        if (tau <= 0) return 0.0
        val w = 2 * PI / response
        val zw = damping * w
        val wd = w * sqrt(1 - damping * damping)
        return 1 - exp(-zw * tau) * (cos(wd * tau) + zw / wd * sin(wd * tau))
    }
    data class LoopState(val hour: Double, val from: Int, val to: Int, val at: Double)
    fun loopState(t: Double): LoopState {
        val hour = when {
            t < T.toNoon[0] -> mix(3.0, 9.0, easeInOut(seg(t, T.toEvening[0], T.toEvening[1])))
            t < T.today -> mix(9.0, 3.0, easeInOut(seg(t, T.toNoon[0], T.toNoon[1])))
            t < T.thu -> mix(3.0, 4.0, easeInOut(seg(t, T.hourUp[0], T.hourUp[1])))
            else -> mix(4.0, 3.0, easeInOut(seg(t, T.hourDown[0], T.hourDown[1])))
        }
        if (t >= T.today && t < T.thu) return LoopState(hour, 2, 0, T.today)
        if (t >= T.thu) return LoopState(hour, 0, 2, T.thu)
        return LoopState(hour, 2, 2, -10.0)
    }
    fun barValue(i: Int, from: Int, to: Int, tau: Double): Double {
        if (from == to) return SunUvBuddyTheme.days[to][i]
        return mix(SunUvBuddyTheme.days[from][i], SunUvBuddyTheme.days[to][i], spring(tau - i * 0.035, 0.42, 0.62))
    }
    fun uvAt(hour: Double, from: Int, to: Int, tau: Double): Double {
        val h = hour.coerceIn(0.0, 10.0)
        val i = floor(h).toInt()
        return mix(barValue(i, from, to, tau), barValue(min(10, i + 1), from, to, tau), h - i)
    }
    fun sizeFor(uv: Double): Double {
        if (uv <= 3) return mix(0.62, 1.0, smooth(uv / 3) * 0.6 + uv / 3 * 0.4)
        return 1 + 0.06 * min(2.0, uv - 3)
    }
    data class Live(val amb: Double = 0.0, val hour: Double = REST_HOUR, val hourV: Double = 0.0,
        val from: Int = 2, val to: Int = 2, val tau: Double = 10.0, val size: Double = 1.0,
        val squish: Double = -1.0, val dayPress: Double = 0.0)
}
