package com.ngocdevv.nativecook.blocks.onboarding.aifeaturetour

import kotlin.math.*

/** Exact analytic functions from aft-motion.ts; time remains Double like JavaScript. */
internal object AiFeatureTourMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val ICONS = 5
    const val ACT = 1.3
    const val FLY_GAP = 0.05
    const val FLY_DUR = 0.55
    const val BACK_GAP = 0.07
    object T {
        const val act0 = 0.3
        const val press = 7.0
        const val fly = 7.1
        const val sparkle = 7.68
        const val back = 8.05
    }
    data class Frame(val amb: Double, val act: List<Double>, val fly: Double, val press: Double)
    data class Flight(val out: Double, val back: Double, val gone: Boolean)
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p
    fun smooth(x: Double): Double {
        val p = clamp01(x)
        return p * p * (3 - 2 * p)
    }
    fun easeOut(x: Double) = 1 - (1 - clamp01(x)).pow(3)
    fun easeIn(x: Double): Double {
        val p = clamp01(x)
        return p * p * p
    }
    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p < 0.5) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2
    }
    fun spring(tau: Double, response: Double, damping: Double): Double {
        if (tau <= 0) return 0.0
        val w = 2 * PI / response
        val zw = damping * w
        val wd = w * sqrt(1 - damping * damping)
        return 1 - exp(-zw * tau) * (cos(wd * tau) + (zw / wd) * sin(wd * tau))
    }
    fun bump(t: Double, at: Double, dur: Double): Double {
        val p = (t - at) / dur
        return if (p <= 0 || p >= 1) 0.0 else sin(PI * p)
    }
    fun frameAt(t: Double): Frame {
        val act = List(ICONS) { i ->
            val p = (t - (T.act0 + i * ACT)) / ACT
            if (p >= 0 && p < 1) p else -1.0
        }
        return Frame(t, act, if (t >= T.press) t - T.press else -1.0, bump(t, T.press - 0.02, 0.22))
    }
    fun flight(i: Int, tau: Double): Flight {
        val at = T.fly - T.press + i * FLY_GAP
        val out = easeIn(seg(tau, at, at + FLY_DUR))
        val backAt = T.back - T.press + i * BACK_GAP
        val back = if (tau >= backAt) spring(tau - backAt, 0.5, 0.6) else 0.0
        return Flight(out, back, tau >= at + FLY_DUR && tau < backAt)
    }
    class Driver {
        var t = 0.0
        var mode = 0
        var last = -10.0
        var amb = 0.0
        val actAt = DoubleArray(ICONS) { -10.0 }
        var flyAt = -10.0
        var pressAt = -10.0
        var pressing = 0
        fun takeOver(now: Double) {
            last = now
            if (mode == 2) return
            mode = 2
            val snapshot = frameAt(t)
            for (i in 0 until ICONS) actAt[i] = if (snapshot.act[i] >= 0) now - snapshot.act[i] * ACT else -10.0
            flyAt = if (snapshot.fly >= 0) now - snapshot.fly else -10.0
        }
        fun step(dt: Double, now: Double, auto: Boolean) {
            amb = (amb + dt) % LOOP
            if (mode == 0) {
                if (auto) t = (t + dt) % LOOP
                return
            }
            if (!auto || now - last < RESUME || pressing == 1) return
            for (i in 0 until ICONS) if (now - actAt[i] < ACT) return
            if (now - flyAt < LOOP - T.press) return
            mode = 0
            t = 0.0
        }
        fun frame(now: Double): Frame {
            if (mode == 0) return frameAt(t)
            val act = actAt.map { at ->
                val p = (now - at) / ACT
                if (p >= 0 && p < 1) p else -1.0
            }
            val fly = if (now - flyAt < LOOP - T.press) now - flyAt else -1.0
            val press = if (pressing == 1) smooth((now - pressAt) / 0.1) else bump(now - flyAt, -0.02, 0.22)
            return Frame(amb, act, fly, press)
        }
    }
}
