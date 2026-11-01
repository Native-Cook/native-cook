package com.ngocdevv.nativecook.blocks.cards.cashbackcloudcard

import kotlin.math.*

internal object CashbackCloudCardMotion {
    const val LOOP = 9.0
    data class Frame(val squeeze: Double = 0.0, val release: Double = 0.0, val rainU: Double = -1.0, val refill: Double = 0.0) {
        val fraction: Double get() = if (rainU < 0) 0.0 else (0 until 6).count { coinK(rainU, it) >= 1 } / 6.0 * (1 - refill)
    }
    fun smoother(k: Double): Double {
        val x = k.coerceIn(0.0, 1.0)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    fun coinK(u: Double, i: Int) = ((u - i * 0.36) / 0.6).coerceIn(0.0, 1.0)
    fun at(time: Double): Frame {
        val t = time % LOOP
        return Frame(
            if (t >= 1 && t < 3.8) smoother((t - 1) / 0.3) else 0.0,
            if (t >= 3.8) exp(-6 * (t - 3.8)) * sin(16 * (t - 3.8)) * 0.4 else 0.0,
            if (t >= 1.3 && t < 7.2) t - 1.3 else -1.0,
            if (t >= 7.2) smoother(t - 7.2) else 0.0)
    }
}
