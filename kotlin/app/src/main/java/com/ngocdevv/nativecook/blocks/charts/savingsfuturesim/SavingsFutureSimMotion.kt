package com.ngocdevv.nativecook.blocks.charts.savingsfuturesim

import kotlin.math.*

internal object SavingsFutureSimMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val MORPH = 1.0
    object T {
        const val press = 1.0
        const val open = 1.15
        val scrub = doubleArrayOf(2.4, 5.4)
        val back = doubleArrayOf(5.7, 6.5)
        const val close = 7.0
    }
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t-a)/(b-a))
    fun mix(a: Double, b: Double, p: Double) = a+(b-a)*p
    fun smooth(x: Double): Double { val p = clamp01(x); return p*p*(3-2*p) }
    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p < 0.5) 4*p*p*p else 1-(-2*p+2).pow(3)/2
    }
    fun spring(tau: Double, response: Double, damping: Double): Double {
        if (tau <= 0) return 0.0
        val w = 2*PI/response
        val zw = damping*w
        val wd = w*sqrt(1-damping*damping)
        return 1-exp(-zw*tau)*(cos(wd*tau)+(zw/wd)*sin(wd*tau))
    }
    fun bump(t: Double, at: Double, dur: Double): Double {
        val p = (t-at)/dur
        return if (p <= 0 || p >= 1) 0.0 else sin(PI*p)
    }
    fun balance(years: Double, rate: Double): Double {
        val r = rate/100
        val g = (1+r).pow(years)
        return 10000*g+200*12*((g-1)/r)
    }
    data class Frame(val amb: Double, var morph: Double, val open: Boolean, val morphAt: Double,
                     val year: Double, val rate: Double, val press: Double, val knob: Double)
    fun frameAt(t: Double): Frame {
        val open = t >= T.open && t < T.close
        val at = if (t >= T.close) T.close else if (t >= T.open) T.open else -10.0
        val morph = if (open) seg(t,T.open,T.open+MORPH) else if (t >= T.close) 1-seg(t,T.close,T.close+MORPH) else 0.0
        var year = 1.0
        if (t >= T.scrub[0]) year = mix(1.0,30.0,easeInOut(seg(t,T.scrub[0],T.scrub[1])))
        if (t >= T.back[0]) year = mix(30.0,10.0,easeInOut(seg(t,T.back[0],T.back[1])))
        return Frame(t,morph,open,t-at,year,6.0,bump(t,T.press-0.02,0.3),0.0)
    }
    class Driver {
        var t = 0.0
        var mode = 0
        var last = -10.0
        var amb = 0.0
        var open = false
        var morphAt = -10.0
        var morphFrom = 0.0
        var year = 1.0
        var rate = 6.0
        var pressing = 0
        var pressAt = -10.0
        var releaseAt = -10.0
        var closeAt = -10.0
        fun morphOf(now: Double): Double {
            val k = seg(now,morphAt,morphAt+MORPH)
            return if (open) mix(morphFrom,1.0,k) else mix(morphFrom,0.0,k)
        }
        fun setOpen(value: Boolean, now: Double) {
            if (open == value) return
            morphFrom = morphOf(now); open = value; morphAt = now
        }
        fun takeOver(now: Double) {
            last = now
            if (mode == 2) return
            val f = frameAt(t)
            mode = 2; open = f.open; morphFrom = if (f.open) 0.0 else 1.0; morphAt = now-f.morphAt
            if (!f.open && f.morph <= 0) { morphFrom = 0.0; morphAt = now-10 }
            year = f.year; rate = f.rate; closeAt = -10.0
        }
        fun step(dt: Double, now: Double, auto: Boolean) {
            amb = (amb+dt)%LOOP
            if (mode == 0) { if (auto) t = (t+dt)%LOOP; return }
            if (closeAt > 0 && now >= closeAt && pressing == 0) { closeAt = -10.0; setOpen(false,now) }
            if (!auto || pressing > 0 || now-last < RESUME) return
            if (open) setOpen(false,now)
            rate += (6-rate)*min(1.0,dt*6)
            if (abs(rate-6) < 0.005) rate = 6.0
            if (!open && morphOf(now) <= 0 && rate == 6.0) { mode = 0; t = 0.0 }
            year = 1.0
        }
        fun frameOf(now: Double): Frame {
            if (mode == 0) return frameAt(t)
            val press = if (pressing == 1) smooth((now-pressAt)/0.15) else 1-smooth((now-releaseAt)/0.15)
            return Frame(amb,morphOf(now),open,now-morphAt,year,rate,
                if (pressing == 1 || now-releaseAt < 0.15) press else 0.0,if (pressing == 3) 1.0 else 0.0)
        }
    }
}
