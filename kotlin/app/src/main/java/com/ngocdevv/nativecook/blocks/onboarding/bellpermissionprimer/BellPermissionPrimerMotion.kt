package com.ngocdevv.nativecook.blocks.onboarding.bellpermissionprimer

import kotlin.math.*

internal object BellPermissionPrimerMotion {
    const val LOOP = 10.0
    const val PERIOD = 1.05
    const val GAP = .13
    const val OPEN_ZETA = .7
    const val OPEN_OMEGA = 4 * PI
    data class Frame(val swing: Double = 0.0, val clap: Double = 0.0, val lift: Double = 0.0, val suck: Double = 0.0, val expand: Double = 0.0, val rings: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0), val finger: Double = 0.0, val fingerAt: Int = 0, val fingerX: Double = 0.0, val fingerPress: Double = 0.0, val bob: Double = 0.0, val time: Double = 0.0, val liftX: Double = 0.0)
    fun seg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0, 1.0)
    fun ease(x: Double) = if (x < .5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun out(x: Double) = 1-(1-x).pow(3)
    fun smooth(x: Double) = x*x*(3-2*x)
    fun openSpring(t: Double): Double {
        if(t<=0) return 0.0
        val wd=OPEN_OMEGA*sqrt(1-OPEN_ZETA*OPEN_ZETA)
        return 1-exp(-OPEN_ZETA*OPEN_OMEGA*t)*(cos(wd*t)+OPEN_ZETA*OPEN_OMEGA/wd*sin(wd*t))
    }
    // Reanimated's 650 ms duration spring solves stiffness using the release velocity, mass 4,
    // perceptual duration × 1.5 and relative energy 6e-9.
    private fun springOmega(z:Double,x0:Double,v0:Double):Double {
        val duration=.65*1.5; val mass=4.0; val threshold=6e-9
        fun fraction(stiffness:Double):Double {
            val omega=sqrt(stiffness/mass)*z
            val x=(x0+(v0+x0*omega)*duration)*exp(-omega*duration)
            val v=-omega*x+(v0+x0*omega)*exp(-omega*duration)
            return (x*x+mass*v*v/stiffness)/(x0*x0+mass*v0*v0/stiffness)-threshold
        }
        var lo=2.220446049250313e-16; var hi=8000.0; var mid=(lo+hi)/2
        repeat(100) {
            if(abs(fraction(mid))<=threshold*1e-3) return sqrt(mid/mass)
            if(fraction(mid)>0) lo=mid else hi=mid
            mid=(lo+hi)/2
        }
        return sqrt(mid/mass)
    }
    class Spring(val from:Double,val to:Double,velocity:Double=0.0,val zeta:Double,omega:Double?=null) {
        private val configuredVelocity=velocity
        // Match withSpring's onStart: only momentum towards the target survives.
        private val v0=if((to>from && velocity<0) || (to<from && velocity>0)) 0.0 else velocity
        private val w=omega ?: if(from==to && v0==0.0) 1.0 else springOmega(zeta,from-to,v0)
        fun sample(t:Double):Pair<Double,Boolean> {
            val x0=from-to; val wd=w*sqrt(1-zeta*zeta); val e=exp(-zeta*w*t)
            val x=e*(x0*cos(wd*t)+(v0+zeta*w*x0)/wd*sin(wd*t))
            val v=-zeta*w*x+e*((v0+zeta*w*x0)*cos(wd*t)-wd*x0*sin(wd*t))
            val initial=x0*x0+configuredVelocity*configuredVelocity/(w*w)
            val finished=initial==0.0 || (x*x+v*v/(w*w))/initial<=6e-9
            return Pair(if(finished) to else to+x,finished)
        }
    }
    fun bounce(x: Double): Double = when { x <= 0 -> 0.0; x >= 1 -> 1.0; x < .62 -> (x/.62).pow(2); else -> { val u=(x-.62)/.38; 1-.09*sin(PI*u)*(1-u) } }
    fun frameAt(t: Double): Frame {
        val calm=1-ease(seg(t,3.1,3.7)); var swing=0.0; var clap=0.0
        if (t>=.9 && t<3.7) {
            val tau=max(0.0,t-1.15); val w=2*PI/PERIOD
            swing=(if(t<1.15) .42*out(seg(t,.9,1.15)) else .42*exp(-.75*tau)*cos(w*tau))*calm
            val raw=if(t<1.15) swing*.7 else .42*1.12*exp(-.6*tau)*cos(w*tau-.9)*calm
            clap=swing+(raw-swing).coerceIn(-GAP,GAP)
        }
        if(t>=8.8) { val tau=t-8.8; val k=1-smooth(seg(t,9.4,9.95)); swing+=.12*exp(-1.6*tau)*sin(2*PI/PERIOD*tau)*k; clap+=.16*exp(-1.4*tau)*sin(2*PI/PERIOD*tau-.7)*k }
        swing+=.012*sin(4*PI*t/LOOP); clap+=.012*sin(4*PI*t/LOOP-.4)
        val lift=ease(seg(t,4.0,4.75))*(if(t<5.05) 1 else 0)
        val suck=if(t<4.75) 0.0 else if(t<8.2) seg(t,4.75,5.05).pow(2) else 1-bounce(seg(t,8.2,8.8))
        val expand=openSpring(t-5.05)*(1-ease(seg(t,7.8,8.2)))
        val strikes=doubleArrayOf(1.42,1.94,2.47); val rings=DoubleArray(3) { i -> if(t>strikes[i] && t<strikes[i]+.9) seg(t,strikes[i],strikes[i]+.9) else 0.0 }
        var finger=0.0; var at=0; var fx=0.0; var press=0.0
        if(t<2) { finger=out(seg(t,.75,.9))*(1-out(seg(t,1.15,1.33))); fx=-.1+.55*out(seg(t,.9,1.15)); press=finger }
        else if(t<6.5) { finger=out(seg(t,3.82,3.98))*(1-out(seg(t,4.75,4.91))); press=finger }
        else { val a=7.7; finger=out(seg(t,a-.15,a-.02))*(1-out(seg(t,a+.14,a+.3))); at=2; press=out(seg(t,a-.02,a+.08))*(1-out(seg(t,a+.12,a+.26))) }
        return Frame(swing,clap,lift,suck,expand,rings,finger,at,fx,press,.015*sin(2*PI*t/LOOP),t)
    }
}
