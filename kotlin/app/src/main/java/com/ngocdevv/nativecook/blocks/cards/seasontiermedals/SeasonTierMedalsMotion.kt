package com.ngocdevv.nativecook.blocks.cards.seasontiermedals

import kotlin.math.*

internal object SeasonTierMedalsMotion {
    const val LOOP = 8.0
    const val SPIN = 0.95
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
    fun smooth(x: Double): Double { val k = clamp(x); return k * k * (3 - 2 * k) }
    fun round(x: Double) = floor(x + 0.5).toInt()
    fun target(t: Double) = if (t >= 1.4 && t < 4.4) 2 else 1
    fun rubber(p: Double) = if (p < 0) -0.35 * tanh(-p / 0.35) else if (p > 2) 2 + 0.35 * tanh((p - 2) / 0.35) else p
    fun yaw(since: Double): Double {
        if (since < 0 || since >= SPIN) return 0.0
        val x = clamp(since / SPIN) - 1
        return 2 * PI * (2.2 * x * x * x + 1.2 * x * x)
    }
    fun flash(since: Double) = if (since > SPIN * 0.75 && since < SPIN + 1.2) exp(-(since - SPIN * 0.75) * 3.2) else 0.0
    class Rail {
        var p = 1.0; var v = 0.0; var finger = 1.0; var stiff = 150.0
        var target = 1; var mode = 0; var landed = -1
        val spins = doubleArrayOf(-10.0, -10.0, -10.0)
        fun spin(index: Int, now: Double) { spins[index] = now; landed = -1 }
        fun step(dt: Double) {
            if (mode == 1) {
                if (dt > 0) v = v * 0.6 + ((finger - p) / dt) * 0.4
                p = finger; return
            }
            val c = 2 * sqrt(stiff) * 0.9
            val n = max(1, ceil(dt / (1.0 / 240)).toInt()); val h = dt / n
            repeat(n) { v += (stiff * (target - p) - c * v) * h; p += v * h }
        }
        fun script(t: Double, dt: Double, now: Double, reduced: Boolean) {
            val tg = SeasonTierMedalsMotion.target(t)
            if (tg != target) { target = tg; stiff = 70.0; if (!reduced) spin(tg, now) }
            step(dt)
        }
    }
}
