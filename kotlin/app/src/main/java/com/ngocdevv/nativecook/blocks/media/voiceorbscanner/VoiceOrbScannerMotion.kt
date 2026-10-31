package com.ngocdevv.nativecook.blocks.media.voiceorbscanner

import kotlin.math.*

internal object VoiceOrbScannerMotion {
    const val LOOP = 9.0
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun smooth(k: Double): Double { val x = clamp(k); return x*x*(3-2*x) }
    fun ease(k: Double): Double { val x = clamp(k); return if (x < 0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2 }
    fun mix(a: Double, b: Double, k: Double) = a+(b-a)*k
    data class Frame(val t: Double = 0.0, val now: Double = 0.0, val l: Double = 0.0, val s: Double = 0.0, val listening: Boolean = false, val useLoopPhase: Boolean = true) {
        val phase get() = if (s > 0.5) 2 else if (listening) 1 else 0
    }
    fun at(t: Double, now: Double = t): Frame {
        val l = if (t >= 1 && t < 3.4) (t-1)/2.4 else if (t >= 3.4 && t < 7.9) 1.0 else 0.0
        val s = if (t >= 3.4 && t < 6.9) ease((t-3.4)/1.1) else if (t >= 6.9 && t < 7.9) 1-ease(t-6.9) else 0.0
        return Frame(t, now, l, s, t >= 1 && t < 3.4)
    }
}
