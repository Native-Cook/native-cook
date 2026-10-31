package com.ngocdevv.nativecook.blocks.modals.firstlootreward

import kotlin.math.*

internal object FirstLootRewardMotion {
    const val LOOP = 4.0
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp((t-a)/(b-a))
    fun mix(a: Double, b: Double, k: Double) = a+(b-a)*k
    fun smooth(k: Double): Double { val x=clamp(k); return x*x*(3-2*x) }
    fun out(k: Double) = 1-(1-clamp(k)).pow(3)
    fun presence(t: Double,a: Double,d: Double) = out(seg(t,a,a+d))
    fun hash(i: Double): Double { val x=sin(i*12.9898+78.233)*43758.5453; return x-floor(x) }
    fun resting(t: Double) = t>1.9 && t<3.55
    data class Chest(val gather: Double=0.0,val lid: Double=0.0,val light: Double=0.0,val since: Double = -1.0,val bump: Double=0.0)
    fun chest(a: Double): Chest {
        if(a<0)return Chest()
        val g=if(a<0.25)smooth(a/0.25) else 1-smooth((a-0.25)/0.06)
        val o=a-0.25
        val lid=if(o>=0)out(seg(o,0.0,0.07))*(1-smooth(seg(o,0.68,0.88))) else 0.0
        val land=o-0.88
        val bump=if(land>=0 && land<0.3)sin(min(land/0.1,1.0)*PI)*exp(-land*8)*0.6 else 0.0
        val light=if(o>=0)smooth(o/0.05)*(1-smooth(seg(o,0.55,0.88))) else 0.0
        return Chest(g,lid,light,o,bump)
    }
    data class Frame(val t: Double,val chest: Chest,val tx: Double=0.0,val ty: Double=0.0,val press: Double=0.0,val charge: Double=0.0,val power: Double=1.0,val keep: Double=0.0,val view: Double=0.0) {
        val ring get()=1-smooth(seg(t,0.12,0.24))
        val badgeA get()=seg(t,0.03,0.21)
        val badgeS get()=if(t<0.233)mix(0.5,1.0,smooth(seg(t,0.04,0.233))) else if(t<0.3)mix(1.0,1.03,1-(1-seg(t,0.233,0.3)).pow(2)) else mix(1.03,1.0,smooth(seg(t,0.3,0.42)))
        val sceneA get()=smooth(seg(t,0.25,0.8))
        val sparkA get()=seg(t,0.45,1.55)
        val fade get()=smooth(seg(t,3.55,3.92))
    }
    fun at(t: Double)=Frame(t,chest(t-0.48))

    // Reanimated 4 duration springs: preserve position and velocity on interruption.
    class Spring {
        private var from=0.0
        private var target=0.0
        private var velocity=0.0
        private var start = -10.0
        private var damping=0.55
        private var stiffness=900.0
        fun sample(now: Double): Pair<Double,Double> {
            val t=max(0.0,now-start); val w=sqrt(stiffness/4); val x=from-target
            val position: Double; val speed: Double
            if(damping>=1) {
                val b=velocity+w*x; val e=exp(-w*t)
                position=target+(x+b*t)*e; speed=(b-w*(x+b*t))*e
            } else {
                val wd=w*sqrt(1-damping*damping); val b=(velocity+damping*w*x)/wd
                val e=exp(-damping*w*t); val q=x*cos(wd*t)+b*sin(wd*t)
                position=target+e*q
                speed=e*(-damping*w*q-x*wd*sin(wd*t)+b*wd*cos(wd*t))
            }
            val initial=0.5*stiffness*x*x
            val energy=0.5*stiffness*(position-target).pow(2)+2*speed*speed
            return if(initial==0.0 || energy/initial<=6e-9) target to 0.0 else position to speed
        }
        fun set(value: Double) { from=value; target=value; velocity=0.0; start = -10.0 }
        fun aim(value: Double,now: Double,seconds: Double,ratio: Double) {
            val s=sample(now); from=s.first; velocity=s.second; target=value; start=now; damping=ratio
            if((value>from && velocity<0) || (value<from && velocity>0))velocity=0.0
            val x=from-target; val d=seconds*1.5
            if(x==0.0 && velocity==0.0)return
            fun diff(k: Double): Double {
                val w=sqrt(k/4)*ratio; val b=velocity+x*w; val e=exp(-w*d)
                val xt=(x+b*d)*e; val vt=(x+b*d)*e*(-w)+b*e
                return (0.5*k*xt*xt+2*vt*vt)/(0.5*k*x*x+2*velocity*velocity)-6e-9
            }
            var lo=Math.ulp(1.0); var hi=8000.0; var mid=(lo+hi)/2
            val direction=if(diff(hi)>=diff(lo))1.0 else -1.0
            for(i in 0 until 100) {
                val f=diff(mid)
                if(abs(f)<=6e-12)break
                if(f*direction<0)lo=mid else hi=mid
                mid=(lo+hi)/2
            }
            stiffness=mid
        }
    }
}
