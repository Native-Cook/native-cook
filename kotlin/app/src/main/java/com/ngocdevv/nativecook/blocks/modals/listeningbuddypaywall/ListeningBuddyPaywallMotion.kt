package com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall

import kotlin.math.*

/** Analytic RN timeline: twelve beats, three plan changes, and the Continue burst. */
internal object ListeningBuddyPaywallMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val BEAT = LOOP / 12
    const val REST_PLAN = 1
    val picks = arrayOf(1.2 to 0, 3.0 to 2, 4.8 to 1)
    const val GO = 6.5
    data class Face(val eyes: Double, val smile: Double, val open: Double, val blush: Double)
    val FACES = arrayOf(Face(1.0, .45, 0.0, .25), Face(0.0, 1.0, 0.0, 1.0), Face(2.0, .3, 1.0, .6))
    data class Frame(val amb: Double, val plan: Int, val from: Int, val selAt: Double, val go: Double, val poke: Double, val press: Double, val closeK: Double)
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun mix(a: Number, b: Number, p: Double) = a.toDouble() + (b.toDouble() - a.toDouble()) * p
    fun smooth(x: Double): Double {
        val p = clamp01(x)
        return p * p * (3 - 2 * p)
    }
    fun easeOut(x: Double) = 1 - (1 - clamp01(x)).pow(3)
    fun spring(tau: Double, response: Double, damping: Double): Double {
        if (tau <= 0) return 0.0
        val w = 2 * PI / response
        val zw = damping * w
        val wd = w * sqrt(1 - damping * damping)
        return 1 - exp(-zw * tau) * (cos(wd * tau) + zw / wd * sin(wd * tau))
    }
    fun bump(t: Double, at: Double, dur: Double): Double {
        val p = (t - at) / dur
        return if (p <= 0 || p >= 1) 0.0 else sin(PI * p)
    }
    fun frameAt(t: Double): Frame {
        var plan = REST_PLAN
        var from = REST_PLAN
        var at = -10.0
        for ((time, target) in picks) if (t >= time) {
            from = plan
            plan = target
            at = time
        }
        return Frame(t, plan, from, t - at, if (t >= GO) t - GO else -1.0, -1.0, bump(t, GO - .02, .3), 0.0)
    }
}
