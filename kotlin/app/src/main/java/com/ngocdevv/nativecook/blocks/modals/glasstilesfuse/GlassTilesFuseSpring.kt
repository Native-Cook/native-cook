package com.ngocdevv.nativecook.blocks.modals.glasstilesfuse

import kotlin.math.*

// Reanimated 4 duration spring, including the bisection, velocity clipping and energy stop.
internal class GlassTilesFuseSpring(val from:Double,val to:Double,duration:Double,val zeta:Double,val configuredVelocity:Double) {
    val velocity=if((to>from&&configuredVelocity<0)||(to<from&&configuredVelocity>0)) 0.0 else configuredVelocity
    private val stiffness:Double
    init {
        val x=from-to; val settle=duration*1.5; val v=velocity
        fun fraction(k:Double):Double {
            val w=sqrt(k/4)*zeta; val env=exp(-w*settle)
            val xt=(x+(v+x*w)*settle)*env; val vt=xt * -w+(v+x*w)*env
            val e0=0.5*k*x*x+2*v*v
            return if(e0==0.0) 0.0 else (0.5*k*xt*xt+2*vt*vt)/e0
        }
        var lo=Math.ulp(1.0); var hi=8000.0; var mid=4000.0; var count=100
        while(abs(fraction(mid)-6e-9)>6e-12&&count>0) {
            count--; if(fraction(mid)>6e-9) lo=mid else hi=mid; mid=(lo+hi)/2
        }
        stiffness=mid
    }
    fun sample(elapsed:Double):Pair<Double,Double> {
        if(elapsed<=0) return from to velocity
        val w=sqrt(stiffness/4); val wd=w*sqrt(1-zeta*zeta); val x=from-to
        val env=exp(-zeta*w*elapsed); val s=sin(wd*elapsed); val c=cos(wd*elapsed)
        val displacement=env*(s*(velocity+zeta*w*x)/wd+x*c)
        val v = -zeta*w*displacement+env*(c*(velocity+zeta*w*x)-wd*x*s)
        val initial=0.5*stiffness*x*x+2*configuredVelocity*configuredVelocity
        if(initial==0.0||(0.5*stiffness*displacement*displacement+2*v*v)/initial<=6e-9) return to to 0.0
        return (to+displacement) to v
    }
}
