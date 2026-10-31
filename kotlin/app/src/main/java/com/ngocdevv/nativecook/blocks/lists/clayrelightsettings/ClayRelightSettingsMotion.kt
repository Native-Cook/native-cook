package com.ngocdevv.nativecook.blocks.lists.clayrelightsettings

import kotlin.math.*

internal object ClayRelightSettingsMotion {
    const val LOOP = 10.0
    const val THEME_UP = 0.62
    const val THEME_DOWN = 0.54
    const val THEME_FADE = 0.3
    data class Frame(
        val m: Double = 0.0, val theme: Double = 0.0, val on: Double = 0.0,
        val knob: Double = 0.0, val press: Double = 0.0, val finger: Double = 0.0,
        val fingerAt: Double = 0.0, val fingerX: Double = 0.0, val grab: Double = 0.0,
    )
    fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t-a)/(b-a))
    fun smooth(a: Double, b: Double, x: Double): Double {
        val u = seg(x,a,b)
        return u*u*(3-2*u)
    }
    fun easeInOut(x: Double) = if (x < 0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    fun settleStep(x: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        return (1-(1+7*x)*exp(-7*x))/(1-8*exp(-7.0))
    }
    fun evening(m: Double): Double {
        val x = clamp01(m)
        return x+0.13*sin(2*PI*x)
    }
    fun sunAngle(m: Double) = 2.12-3.6*evening(m)
    fun moonAngle(m: Double) = PI+0.16-1.18*smooth(0.42,1.0,evening(m))
    fun night(m: Double) = smooth(0.42,0.86,evening(m))
    fun dusk(m: Double): Double {
        val e = evening(m)
        return smooth(0.12,0.44,e)*(1-smooth(0.5,0.8,e))
    }
    fun lamp(m: Double, row: Int) = smooth(0.5+0.045*row,0.6+0.045*row,evening(m))
    fun windows(m: Double) = smooth(0.44,0.56,evening(m))
    fun mAt(t: Double): Double {
        if (t < 0.9) return 0.0
        if (t < 4.7) return smooth(0.0,1.0,seg(t,0.9,3.9))
        if (t < 6) return 1-0.48*easeInOut(seg(t,4.7,5.6))
        if (t < 7.7) return 0.52+0.48*settleStep(seg(t,6.0,7.3))
        return 1-smooth(0.0,1.0,seg(t,7.7,9.8))
    }
    private val steps: List<Double> = run {
        val out = mutableListOf<Double>()
        var state = false
        var t = 0.0
        while (t < LOOP) {
            val m = mAt(t)
            if (!state && m > THEME_UP) { state = true; out.add(t) }
            else if (state && m < THEME_DOWN) { state = false; out.add(t) }
            t += 0.001
        }
        while (out.size < 4) out.add(LOOP+10)
        out
    }
    fun themeAt(t: Double): Double {
        fun up(a: Double) = easeInOut(seg(t,a,a+THEME_FADE))
        return clamp01(up(steps[0])-up(steps[1])+up(steps[2])-up(steps[3]))
    }
    fun frameAt(t: Double): Frame {
        fun tapPress(a: Double) = easeOut(seg(t,a-0.08,a))*(1-easeOut(seg(t,a+0.06,a+0.2)))
        fun near(a: Double, b: Double) = easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.25)))
        val onSky = near(4.7,6.0)
        return Frame(mAt(t),themeAt(t),if (t >= 0.9 && t < 7.7) 1.0 else 0.0,
            easeOut(seg(t,0.9,1.18))*(1-easeOut(seg(t,7.7,7.98))),
            clamp01(tapPress(0.9)+tapPress(7.7)),
            clamp01(near(0.85,1.1)+near(7.65,7.9)+onSky),
            if (onSky > 0.001) 1.0 else 0.0,easeInOut(seg(t,4.7,5.6)))
    }
    fun clockText(m: Double): String {
        val mins = floor((880+evening(m)*540)/5+0.5).toInt()*5
        val hour = (mins/60)%24
        return "%d:%02d %s".format(if (hour%12 == 0) 12 else hour%12,mins%60,if (hour < 12) "AM" else "PM")
    }
    fun bezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        fun axis(t: Double, a: Double, b: Double) = 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
        var lo = 0.0
        var hi = 1.0
        repeat(24) {
            val mid = (lo+hi)/2
            if (axis(mid,x1,x2) < x) lo = mid else hi = mid
        }
        return axis((lo+hi)/2,y1,y2)
    }
}
