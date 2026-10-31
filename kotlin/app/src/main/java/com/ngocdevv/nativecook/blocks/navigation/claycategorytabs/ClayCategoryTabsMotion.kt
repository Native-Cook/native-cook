package com.ngocdevv.nativecook.blocks.navigation.claycategorytabs

import kotlin.math.*

internal object ClayCategoryTabsMotion {
    const val LOOP = 9.0
    fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t-a)/(b-a))
    fun smooth(a: Double, b: Double, x: Double): Double { val u = seg(x,a,b); return u*u*(3-2*u) }
    fun easeInOut(x: Double) = if (x < 0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    fun landSquash(u: Double) = if (u <= 0 || u >= 0.9) 0.0 else 0.6*exp(-u*8)*sin(u*22)
    fun ringKnob(u: Double) = if (u <= 0 || u >= 0.3) 0.0 else sin(PI*u/0.3)
    fun ringWobble(u: Double) = if (u <= 0 || u >= 1.2) 0.0 else 0.16*exp(-u*4.5)*sin(u*30)
    fun rAt(t: Double): Double {
        if (t < 1.05) return 0.0
        if (t < 3.2) return easeInOut(seg(t,1.05,2.15))
        if (t < 4.3) return 1+0.5*easeInOut(seg(t,3.2,3.9))
        if (t < 4.7) return 1.5+0.32*easeInOut(seg(t,4.3,4.7))
        if (t < 6.65) return 1.82+0.18*easeOut(seg(t,4.7,5.2))
        return 2-2*easeInOut(seg(t,6.65,8.15))
    }
    data class Frame(val r: Double = 0.0, val squash: Double = 0.0, val knob: Double = 0.0,
        val wobble: Double = 0.0, val finger: Double = 0.0, val onPage: Double = 0.0,
        val tab: Int = 0, val dragX: Double = 0.0, val press: Double = 0.0)
    fun frameAt(t: Double): Frame {
        fun near(a: Double, b: Double) = easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.25)))
        fun tapPress(a: Double) = easeOut(seg(t,a-0.08,a))*(1-easeOut(seg(t,a+0.06,a+0.2)))
        val onTap = near(0.95,1.2)+near(6.55,6.8)
        val onDrag = near(3.2,4.7)
        return Frame(rAt(t),landSquash(t-2.15)+landSquash(t-5.2)+landSquash(t-8.15),
            ringKnob(t-5.2),ringWobble(t-5.2),clamp01(onTap+onDrag),
            if (onDrag > 0.001) 1.0 else 0.0,if (t < 4) 1 else 0,(rAt(t)-1)/0.82,
            clamp01(tapPress(1.0)+tapPress(6.6)))
    }
    fun bezier(u0: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val u = clamp01(u0)
        fun curve(t: Double, a: Double, b: Double) = 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
        var lo = 0.0; var hi = 1.0
        repeat(32) { val mid = (lo+hi)/2; if (curve(mid,x1,x2) < u) lo = mid else hi = mid }
        return curve((lo+hi)/2,y1,y2)
    }
    fun defaultEase(u: Double) = if (u < 0.5) 2*u*u else 1-(-2*u+2).pow(2)/2
    fun touchEase(u: Double) = bezier(u,0.77,0.0,0.175,1.0)
    fun touchOut(u: Double) = bezier(u,0.23,1.0,0.32,1.0)
}

internal class ClayCategoryTabsSpring(val from: Double, val to: Double, velocity: Double = 0.0,
    val duration: Double, val damping: Double) {
    val velocity = if ((to > from && velocity < 0) || (to < from && velocity > 0)) 0.0 else velocity
    private val stiffness: Double
    init {
        val x = from-to; val v = this.velocity; val d = duration*1.5
        fun fraction(k: Double): Double {
            val om = sqrt(k/4)*damping; val envelope = exp(-om*d)
            val xt = (x+(v+x*om)*d)*envelope
            val vt = xt*(-om)+(v+x*om)*envelope
            return (k*xt*xt+4*vt*vt)/max(1e-30,k*x*x+4*v*v)-6e-9
        }
        var lo = Math.ulp(1.0); var hi = 8000.0
        var mid = (lo+hi)/2
        for (i in 0 until 100) {
            mid = (lo+hi)/2
            val f = fraction(mid)
            if (abs(f) < 6e-12) break
            if (f > 0) lo = mid else hi = mid
        }
        stiffness = mid
    }
    fun sample(elapsed: Double): Pair<Double, Boolean> {
        val t = max(0.0,elapsed); val om = sqrt(stiffness/4); val w = om*sqrt(1-damping*damping)
        val x = from-to; val a = (velocity+damping*om*x)/w
        val e = exp(-damping*om*t); val wave = x*cos(w*t)+a*sin(w*t)
        val displacement = e*wave
        val v = -damping*om*displacement+e*(-x*w*sin(w*t)+a*w*cos(w*t))
        val initial = stiffness*x*x+4*velocity*velocity
        val done = initial == 0.0 || (stiffness*displacement*displacement+4*v*v)/initial <= 6e-9
        return Pair(if (done) to else to+displacement,done)
    }
}
