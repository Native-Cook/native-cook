package com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding

import kotlin.math.*

internal object MorphObjectOnboardingMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    val moves = arrayOf(doubleArrayOf(1.5, 2.6, 0.0, 1.0), doubleArrayOf(4.6, 5.7, 1.0, 2.0), doubleArrayOf(7.6, 8.7, 2.0, 0.0))
    val holdAt = doubleArrayOf(0.0, 2.6, 5.7)
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p
    fun smooth(x: Double): Double {
        val p = clamp01(x)
        return p * p * (3 - 2 * p)
    }

    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p < 0.5) 16 * p.pow(5) else 1 - (-2 * p + 2).pow(5) / 2
    }

    fun easeInOutCubic(x: Double): Double {
        val p = clamp01(x)
        return if (p < 0.5) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2
    }

    fun wobble(tau: Double, period: Double, decay: Double) = if (tau <= 0) 0.0 else exp(-tau / decay) * cos(2 * PI * tau / period)
    // JS Math.round rounds ties toward positive infinity (unlike Kotlin's round).
    fun pageRound(p: Double) = floor(p + 0.5)
    fun pageAt(t: Double): Double {
        for (i in moves.indices) {
            val m = moves[i]
            if (t < m[0]) return if (i == 0) 0.0 else moves[i - 1][3]
            if (t < m[1]) return mix(m[2], m[3], easeInOutCubic(seg(t, m[0], m[1])))
        }

        return 0.0
    }

    fun arrivalAt(t: Double): Double {
        for (i in 0..1) {
            val tau = t - moves[i][1]
            if (tau >= 0 && tau < 1) return tau
        }

        return -1.0
    }

    fun band(p: Double): Double {
        if (p < 0) return -(1 - 1 / (1 - p * 1.6)) / 1.6
        if (p > 2) return 2 + (1 - 1 / (1 + (p - 2) * 1.6)) / 1.6
        return p
    }

    data class Live(val amb: Double = 0.0, val p: Double = 0.0, val arrive: Double = -1.0, val hop: Double = -1.0, val press: Double = 0.0)
}
