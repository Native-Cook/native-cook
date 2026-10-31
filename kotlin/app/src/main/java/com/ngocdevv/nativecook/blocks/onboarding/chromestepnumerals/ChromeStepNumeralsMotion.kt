package com.ngocdevv.nativecook.blocks.onboarding.chromestepnumerals

import kotlin.math.*

internal object ChromeStepNumeralsMotion {
    const val LOOP = 9.0
    const val STEPS = 3
    fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t-a)/(b-a))
    fun easeInOut(x: Double) = if (x < 0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    fun yawOf(r: Double) = 0.3+2*PI*r
    object T {
        const val next = 1.2
        val go1 = 1.3 to 2.5
        val drag = 3.4 to 4.4
        val more = 4.9 to 5.4
        val settle = 5.4 to 5.9
        val back = 7.0 to 8.6
    }
    fun rAt(t: Double): Double = when {
        t < T.go1.first -> 0.0
        t < T.drag.first -> easeInOut(seg(t,T.go1.first,T.go1.second))
        t < T.more.first -> 1+0.5*easeInOut(seg(t,T.drag.first,T.drag.second))
        t < T.settle.first -> 1.5+0.3*easeInOut(seg(t,T.more.first,T.more.second))
        t < T.back.first -> 1.8+0.2*easeOut(seg(t,T.settle.first,T.settle.second))
        else -> 2-2*easeInOut(seg(t,T.back.first,T.back.second))
    }
    data class Frame(val r: Double=0.0, val bob: Double=0.0, val press: Double=0.0,
        val finger: Double=0.0, val fingerX: Double=0.0, val fingerAt: Int=1)
    fun frameAt(t: Double): Frame {
        val r=rAt(t)
        fun tap(a: Double)=easeOut(seg(t,a-0.08,a))*(1-easeOut(seg(t,a+0.06,a+0.2)))
        fun near(a: Double,b: Double)=easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.2)))
        val onNext=near(T.next-0.05,T.next+0.15)
        val onDrag=near(T.drag.first,T.more.second)
        val onBack=near(T.back.first,T.back.second)
        return Frame(r,0.04*sin(2*PI*2*t/LOOP),tap(T.next),clamp01(onNext+onDrag+onBack),
            if(onBack>0.001) 0.2+0.6*easeInOut(seg(t,T.back.first,T.back.second)) else 0.75-(r-1)*0.6,
            if(onNext>0.001) 0 else 1)
    }
    fun timing(x: Double): Double {
        fun curve(t: Double,a: Double,b: Double)=3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
        var lo=0.0; var hi=1.0
        repeat(40) { val m=(lo+hi)/2; if(curve(m,0.77,0.175)<clamp01(x)) lo=m else hi=m }
        return curve((lo+hi)/2,0.0,1.0)
    }
    fun rubber(v: Double)=when {
        v<0 -> -0.1*(1-exp(v/0.1))
        v>2 -> 2+0.1*(1-exp(-(v-2)/0.1))
        else -> v
    }
    // Same duration/energy solver as Reanimated's duration spring, with velocity in steps/second.
    class Move(var start: Double,val from: Double,val to: Double,initialVelocity: Double,reduced: Boolean) {
        val velocity=if((to-from)*initialVelocity<0) 0.0 else initialVelocity
        val duration=if(reduced) 0.3 else if(initialVelocity==0.0) 1.1+0.3*abs(to-from) else 0.9
        val omega: Double
        init {
            if(!reduced && initialVelocity!=0.0) {
                val x=from-to; val v=this.velocity
                fun diff(stiffness: Double): Double {
                    val w=sqrt(stiffness/4)*0.9; val t=1.35; val envelope=exp(-w*t)
                    val xt=(x+(v+x*w)*t)*envelope
                    val vt= -w*xt+(v+x*w)*envelope
                    return (stiffness*xt*xt+4*vt*vt)/max(1e-300,stiffness*x*x+4*v*v)-6e-9
                }
                var lo=2.220446049250313e-16; var hi=8000.0
                val direction=if(diff(hi)>=diff(lo)) 1.0 else -1.0
                for(i in 0 until 100) {
                    val m=(lo+hi)/2; val d=diff(m)
                    if(abs(d)<=6e-12) { lo=m; hi=m; break }
                    if(d*direction<0) lo=m else hi=m
                }
                omega=sqrt(((lo+hi)/2)/4)
            } else omega=0.0
        }
        fun sample(now: Double): Pair<Double,Boolean> {
            val t=max(0.0,now-start)
            if(omega==0.0) return from+(to-from)*timing(t/duration) to (t>=duration)
            val x=from-to; val wd=omega*sqrt(1-0.9*0.9); val e=exp(-0.9*omega*t)
            val a=(velocity+0.9*omega*x)/wd
            val displacement=e*(x*cos(wd*t)+a*sin(wd*t))
            val v= -0.9*omega*displacement+e*(-x*wd*sin(wd*t)+a*wd*cos(wd*t))
            val energy=(omega*omega*displacement*displacement+v*v)/max(1e-300,omega*omega*x*x+velocity*velocity)
            return to+displacement to (energy<=6e-9)
        }
    }
}
