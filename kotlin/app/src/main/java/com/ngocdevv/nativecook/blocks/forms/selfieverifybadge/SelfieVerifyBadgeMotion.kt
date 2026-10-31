package com.ngocdevv.nativecook.blocks.forms.selfieverifybadge

import kotlin.math.*

internal object SelfieVerifyBadgeMotion {
    const val LOOP = 9.6
    fun clamp01(v: Double) = v.coerceIn(0.0,1.0)
    fun seg(t: Double,a: Double,b: Double) = clamp01((t-a)/(b-a))
    fun easeInOut(x: Double) = if(x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    fun mix(a: Double,b: Double,t: Double) = a+(b-a)*t
    fun springTo(t: Double,response: Double,zeta: Double): Double {
        if(t<=0) return 0.0
        val w=2*PI/response; val wd=w*sqrt(1-zeta*zeta)
        return 1-exp(-zeta*w*t)*(cos(wd*t)+zeta*w/wd*sin(wd*t))
    }
    class Layout(val w: Double,val h: Double,top: Double,bottom: Double) {
        val frame=min(122.0,w*0.3)
        val titleTop=top+34; val frameX=w/2; val frameY=top+120+frame+30
        val hintTop=frameY+frame+40; val shutterY=h-bottom-74; val shutter=72.0; val cover=hypot(w,h)*0.8
    }
    data class Verified(val brackets: Double,val collapse: Double,val bust: Double,val shape: Double,
        val puff: Double,val size: Double,val badge: Double,val yaw: Double,val check: Double,val light: Double)
    fun verified(l: Layout,k: Double): Verified {
        val shape=easeInOut(seg(k,0.06,0.3));val puff=easeInOut(seg(k,0.2,0.55))
        val grow=easeInOut(seg(k,0.45,0.72));val flipIn=seg(k,0.55,0.9)
        return Verified(1-easeOut(seg(k,0.0,0.12)),easeInOut(seg(k,0.0,0.3)),1-easeInOut(seg(k,0.24,0.36)),
            shape,puff,mix(mix(l.frame,l.frame*0.82,shape),l.cover,grow),
            56*springTo(flipIn*0.44,0.42,0.55),(1-easeOut(flipIn))*PI*3,
            easeInOut(seg(k,0.86,1.0)),easeInOut(seg(k,0.55,0.7)))
    }
    fun scanAt(t: Double): Double {
        if(t<0.8 || t>=1.7) return -9.0
        val u=seg(t,0.8,1.7)
        return mix(-0.52,0.58,easeInOut(if(u<0.5) u*2 else 2-u*2))
    }
    fun detectedAt(t: Double)=easeOut(seg(t,1.7,1.95))*(1-easeOut(seg(t,8.9,9.2)))
    fun linkAt(t: Double)=if(t<2.35) 0.0 else if(t<7.6) seg(t,2.35,5.6) else 1-easeInOut(seg(t,7.6,8.9))
    fun flashAt(t: Double): Double {val u=seg(t,2.33,2.55);return if(u>0 && u<1) sin(u*PI)*(1-u) else 0.0}
    fun shutterAt(t: Double)=easeOut(seg(t,2.2,2.3))*(1-easeOut(seg(t,2.34,2.54)))
    fun bezier(x: Double,x1: Double=0.77,y1: Double=0.0,x2: Double=0.175,y2: Double=1.0): Double {
        fun at(t: Double,a: Double,b: Double)=3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
        var lo=0.0;var hi=1.0
        repeat(24){val m=(lo+hi)/2;if(at(m,x1,x2)<x) lo=m else hi=m}
        return at((lo+hi)/2,y1,y2)
    }
    // Same duration spring solver and energy termination as Reanimated 4.
    fun releaseStiffness(x0: Double,velocity: Double): Double {
        if(x0==0.0 && velocity==0.0) return 900.0
        fun energyDiff(k: Double): Double {
            val omega=sqrt(k/4)*0.45;val settling=0.7*1.5
            val xt=(x0+(velocity+x0*omega)*settling)*exp(-omega*settling)
            val vt=xt * -omega+(velocity+x0*omega)*exp(-omega*settling)
            return (0.5*k*xt*xt+2*vt*vt)/(0.5*k*x0*x0+2*velocity*velocity)-6e-9
        }
        var lo=Math.ulp(1.0);var hi=8000.0;var current=(lo+hi)/2
        val direction=if(energyDiff(hi)>=energyDiff(lo)) 1.0 else -1.0
        for(i in 0 until 100) {
            if(abs(energyDiff(current))<=6e-12) break
            if(energyDiff(current)*direction<0) lo=current else hi=current
            current=(lo+hi)/2
        }
        return current
    }
    data class Frame(var link: Double=0.0,var detected: Double=0.0,var scan: Double = -9.0,var flash: Double=0.0,
        var shutter: Double=0.0,var yaw: Double=0.0,var spin: Double=0.0,var fade: Double=0.0)
    fun frameAt(t: Double)=Frame(linkAt(t),detectedAt(t),scanAt(t),flashAt(t),shutterAt(t),sin(t/LOOP*PI*2)*0.32)
}
