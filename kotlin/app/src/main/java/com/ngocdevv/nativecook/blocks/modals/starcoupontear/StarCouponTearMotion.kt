package com.ngocdevv.nativecook.blocks.modals.starcoupontear

import kotlin.math.*

internal object StarCouponTearMotion {
    const val LOOP = 9.0
    const val PULL_PT = 150.0
    const val CLAIM_AT = 0.6
    data class Frame(val pull: Double = 0.0, val tear: Double = 0.0, val fly: Double = 0.0,
                     val pop: Double = 1.0, val chip: Double = 1.0, val finger: Double = 0.0)
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t-a)/(b-a))
    fun smooth(a: Double, b: Double, x: Double): Double { val u=seg(x,a,b); return u*u*(3-2*u) }
    fun easeInOut(x: Double) = if (x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    fun backOut(x: Double): Double { val u=clamp01(x)-1; return 1+2.6*u*u*u+1.6*u*u }
    fun tearOf(pull: Double) = smooth(0.12,1.0,pull)
    fun frameAt(t: Double): Frame {
        val pull=if (t>=1.1 && t<2.9) 0.5*easeInOut(seg(t,1.1,2.0))+0.35*easeInOut(seg(t,2.4,2.8)) else 0.0
        val after=if (t>=2.9 && t<4.5) 0.85*(1-easeOut(seg(t,2.9,3.3))) else 0.0
        val tear=if (t<2.9) tearOf(pull) else if (t<4.5) tearOf(0.85)+(1-tearOf(0.85))*easeOut(seg(t,2.9,3.3)) else 0.0
        return Frame(if (t<2.9) pull else after,tear,
            if (t<4.5) easeInOut(seg(t,3.8,4.5)) else 0.0,
            if (t<4.5) 1.0 else seg(t,5.2,5.8),
            if (t<4.5) 1.0 else seg(t,4.5,5.3),
            easeOut(seg(t,0.75,1.0))*(1-easeOut(seg(t,2.9,3.1))))
    }
    fun bezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        fun b(t: Double,a: Double,c: Double) = 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*c+t*t*t
        var lo=0.0; var hi=1.0
        repeat(24) { val m=(lo+hi)/2; if (b(m,x1,x2)<clamp01(x)) lo=m else hi=m }
        return b((lo+hi)/2,y1,y2)
    }
    fun defaultEase(x: Double): Double { val u=clamp01(x); return if(u<0.5) 2*u*u else 1-(-2*u+2).pow(2)/2 }
    // Reanimated 4 duration spring, mass=4 and energyThreshold=6e-9.
    fun spring(t: Double,duration: Double,damping: Double): Double {
        if (t<=0) return 0.0
        val w=if(duration==0.4) { if(damping==0.6) 33.82731306762333 else 25.56132523278576 }
              else { if(damping==0.55) 32.74919488514183 else 22.720813180044853 }
        val wd=w*sqrt(1-damping*damping); val envelope=exp(-damping*w*t)
        val displacement=envelope*(cos(wd*t)+damping*w/wd*sin(wd*t))
        val velocity = -damping*w*displacement+envelope*(damping*w*cos(wd*t)-wd*sin(wd*t))
        if(displacement*displacement+velocity*velocity/(w*w)<=6e-9) return 1.0
        return 1-displacement
    }
}
