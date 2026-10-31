package com.ngocdevv.nativecook.blocks.modals.perfectmonthgem

import kotlin.math.*

internal object PerfectMonthGemMotion {
    const val LOOP = 8.0
    fun clamp(v: Double) = v.coerceIn(0.0, 1.0)
    fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
    fun smooth(k: Double): Double { val x = clamp(k); return x * x * (3 - 2 * x) }
    fun ease(k: Double): Double { val x = clamp(k); return if (x < .5) 4*x*x*x else 1 - (-2*x+2).pow(3)/2 }
    fun back(k: Double): Double { val x = clamp(k); return 1 + 2.6*(x-1).pow(3) + 1.6*(x-1).pow(2) }
    fun progress(t: Double): Double = when {
        t < .6 -> 0.0
        t < 2.8 -> ease((t-.6)/2.2)
        t < 6.6 -> 1.0
        t < 7.6 -> 1-ease(t-6.6)
        else -> 0.0
    }
}

internal class PerfectMonthGemDriver {
    var time = 0.0
    var now = 0.0
    var last = -10.0
    var p = 0.0
    var velocity = 0.0
    var target = 1.0
    var held = false
    var start = 0.0
    var forged = false
    fun step(elapsed: Double, auto: Boolean, reduced: Boolean, forge: () -> Unit) {
        now += elapsed
        val dt = min(elapsed, .05)
        val playing = auto && !reduced && !held && now-last >= 2
        if (playing) {
            time = (time+elapsed)%PerfectMonthGemMotion.LOOP
            p = PerfectMonthGemMotion.progress(time)
            velocity = 0.0
            target = if (p > .5) 1.0 else 0.0
        } else if (!held) {
            if (reduced) p += (target-p)*(1-exp(-dt*16))
            else {
                velocity += (170*(target-p)-2*sqrt(170.0)*.8*velocity)*dt
                p += velocity*dt
            }
        }
        if (held || !playing) {
            if (p > .92 && !forged) { forged = true; forge() }
            else if (p < .6) forged = false
        }
    }
    fun begin() { last = now }
    fun drag(dy: Double) {
        last = now
        var next = start-dy/180
        if (next > 1) next = 1+.08*tanh((next-1)/.08)
        if (next < 0) next = -.08*tanh(-next/.08)
        p = next
    }
    fun end(vy: Double) {
        last = now; held = false; velocity = -vy/180
        target = if (p+velocity*.15 > .5) 1.0 else 0.0
    }
    fun tap(): String {
        last = now
        target = if (target > .5 && p > .5) 0.0 else 1.0
        return if (target == 1.0) "gather" else "tap"
    }
    fun accessibleToggle() { last = now; target = if (p > .5) 0.0 else 1.0 }
    fun turn(auto: Boolean, reduced: Boolean) = 2*PI*(if (auto && !reduced && !held) time else now)/PerfectMonthGemMotion.LOOP
}
