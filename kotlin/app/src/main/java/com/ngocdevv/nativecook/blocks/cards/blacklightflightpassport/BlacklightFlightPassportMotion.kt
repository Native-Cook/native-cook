package com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Pure RN timeline: seconds, identical quintic easing, frame LOOP equals frame zero. */
internal object BlacklightFlightPassportMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val UV_PEAK = 5.2
    object T {
        val fade = doubleArrayOf(0.5, 0.85)
        val draw = doubleArrayOf(0.9, 3.2)
        val press = doubleArrayOf(3.55, 3.72)
        val uvOn = doubleArrayOf(3.72, 4.55)
        val glow = doubleArrayOf(4.4, 5.2)
        val pressOff = doubleArrayOf(6.75, 6.9)
        val uvOff = doubleArrayOf(6.9, 7.6)
    }
    data class Frame(
        val t: Double, val routes: DoubleArray, val fade: Double, val count: Double,
        val press: Double, val uv: Double, val glow: Double,
    )
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeOut(x: Double): Double {
        val u = 1 - clamp01(x)
        return 1 - u * u * u * u * u
    }
    fun easeOutCubic(x: Double): Double {
        val u = 1 - clamp01(x)
        return 1 - u * u * u
    }
    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p < 0.5) 16 * p * p * p * p * p else 1 - (-2 * p + 2).pow(5) / 2
    }
    fun smooth(x: Double): Double {
        val p = clamp01(x)
        return p * p * (3 - 2 * p)
    }
    fun routeAt(route: Int, t: Double): Double {
        if (t < T.fade[0] || t >= T.draw[1] + 0.2) return 1.0
        if (t < T.draw[0]) return 1.0
        val start = T.draw[0] + route / 17.0 * (T.draw[1] - T.draw[0] - 0.55)
        return easeInOut(seg(t, start, start + 0.55))
    }
    fun routeFade(t: Double): Double {
        return if (t >= T.fade[0] && t < T.draw[0]) 1 - smooth(seg(t, T.fade[0], T.fade[1])) else 1.0
    }
    fun frameAt(t: Double): Frame {
        val counting = t >= T.fade[0] && t < T.draw[1] + 0.3
        val count = if (counting) {
            if (t < T.draw[0]) 1 - smooth(seg(t, T.fade[0], T.fade[1]))
            else easeOutCubic(seg(t, T.draw[0], T.draw[1]))
        } else 1.0
        val pOn = seg(t, T.press[0], T.press[1])
        val pOff = seg(t, T.pressOff[0], T.pressOff[1])
        val press = when {
            pOn > 0 && pOn < 1 -> sin(PI * pOn)
            pOff > 0 && pOff < 1 -> sin(PI * pOff)
            else -> 0.0
        }
        val uv = if (t < T.uvOff[0]) easeInOut(seg(t, T.uvOn[0], T.uvOn[1]))
            else 1 - easeInOut(seg(t, T.uvOff[0], T.uvOff[1]))
        val glow = if (t < T.uvOff[0]) smooth(seg(t, T.glow[0], T.glow[1]))
            else 1 - smooth(seg(t, T.uvOff[0], T.uvOff[0] + 0.3))
        return Frame(t, DoubleArray(18) { routeAt(it, t) }, routeFade(t), count, press, uv, glow)
    }
    fun isUv(t: Double) = t >= T.uvOn[0] + 0.3 && t < T.uvOff[0]
}
