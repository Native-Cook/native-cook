package com.ngocdevv.nativecook.blocks.media.glassaperturecamera

import kotlin.math.*

internal object GlassApertureCameraMotion {
    const val LOOP = 10.0
    const val NEAR = 0.2
    object Shot {
        const val GO = 0.3
        const val END = 1.5
        const val K_TO = 0.14
    }
    data class ShotPose(val y: Double=0.0,val k: Double=1.0,val rot: Double=0.0,val stretch: Double=0.0,val alpha: Double=0.0,val bump: Double=0.0)
    private fun seg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun ease(x: Double) = if (x < .5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun out(x: Double) = 1-(1-x).pow(3)
    fun bezier(x: Double): Double {
        val target=x.coerceIn(0.0,1.0); var lo=0.0; var hi=1.0
        repeat(24) { val t=(lo+hi)/2; val v=3*(1-t)*(1-t)*t*.77+3*(1-t)*t*t*.175+t*t*t; if(v<target)lo=t else hi=t }
        val t=(lo+hi)/2; return 3*(1-t)*t*t+t*t*t
    }
    fun quad(x: Double): Double { val t=x.coerceIn(0.0,1.0); return if(t<.5)2*t*t else 1-(-2*t+2).pow(2)/2 }
    fun fAt(u: Double) = 1.4*(16.0/1.4).pow(u.coerceIn(0.0,1.0))
    fun uOf(f: Double) = ln(f/1.4)/ln(16.0/1.4)
    fun springAt(t: Double,from: Double,to: Double,v0: Double,zeta: Double,omega: Double): Double {
        if(t<=0) return from
        val x0=from-to; val wd=omega*sqrt(1-zeta*zeta); val b=(v0+zeta*omega*x0)/wd
        return to+exp(-zeta*omega*t)*(x0*cos(wd*t)+b*sin(wd*t))
    }
    private fun shotY(f: Double)=springAt(f,0.0,1.0,-3.5,.66,8.0)
    val LAND: Double=run { var f=0.0; while(f<2 && shotY(f)<1) f+=.001; f }
    fun shotAt(s: Double): ShotPose {
        val f=s-Shot.GO
        if(s<0 || f<=0) return ShotPose()
        val y=shotY(f)
        val vy=(shotY(f+.004)-shotY(max(0.0,f-.004)))/(if(f>.004).008 else .004+f)
        val k=springAt(f,1.0,Shot.K_TO,0.0,.82,11.0)
        val rot=springAt(f,0.0,-6.0,28.0,.5,11.0)
        val tau=f-LAND; val bump=if(tau>0)exp(-7*tau)*sin(26*tau) else 0.0
        return ShotPose(y,k,rot,(vy/6).coerceIn(-1.0,1.0),1-seg(f,LAND+.05,LAND+.4),bump)
    }
    data class Frame(val fnum: Double=2.8,val iris: Double=0.0,val focus: Double=NEAR,val snap: Double=0.0,val shot: Double= -1.0,val sent: Double=0.0,val ring: Double= -1.0,val finger: Double=0.0,val fingerAt: Int=0,val spread: Double=80.0,val press: Double=0.0)
    fun frameAt(t: Double): Frame {
        val u0=uOf(2.8); val u11=uOf(11.0)
        val u=when { t>=3.8 -> u11+(u0-u11)*ease(seg(t,3.8,4.4)); t>=2.2 -> u11*ease(seg(t,2.2,3.4)); t>=.9 -> u0*(1-ease(seg(t,.9,1.8))); else -> u0 }
        val iris=out(seg(t,.6,.9))*(1-ease(seg(t,4.5,5.0)))
        val focus=NEAR+.72*(ease(seg(t,5.4,6.0))-ease(seg(t,7.0,7.6)))
        val a=seg(t,7.8,8.15); val snap=if(a<.35)ease(a/.35) else 1-out((a-.35)/.65)
        val shot=if(t>=7.8 && t<7.8+Shot.END)t-7.8 else -1.0
        val sent=out(seg(t,8.52,8.77))*(1-ease(seg(t,9.35,9.6)))
        val ring=if(t>=5.4 && t<6.6)t-5.4 else if(t>=7.0 && t<8.2)t-7 else -1.0
        val pinch=out(seg(t,.4,.6))*(1-out(seg(t,4.4,4.6)))
        val far=out(seg(t,5.22,5.37))*(1-out(seg(t,5.5,5.66)))
        val near=out(seg(t,6.82,6.97))*(1-out(seg(t,7.1,7.26)))
        val shutter=out(seg(t,7.62,7.77))*(1-out(seg(t,7.9,8.06)))
        val finger=maxOf(pinch,far,near,shutter); val at=when(finger){far -> if(far>0)1 else 0; near -> if(near>0)3 else 0; shutter -> if(shutter>0)2 else 0; else -> 0}
        return Frame(fAt(u),iris,focus,snap,shot,sent,ring,finger,at,60+90*(1-u/u11),finger)
    }
    fun depthAt(u: Double,v: Double): Double {
        fun smooth(a: Double,b: Double,x: Double): Double { val t=((x-a)/(b-a)).coerceIn(0.0,1.0); return t*t*(3-2*t) }
        var d=1-hypot((u-.36)*1.5,v-.64).times(1.9).coerceIn(0.0,1.0)*.8
        val sky=smooth(.2,.26,u)*(1-smooth(.56,.62,u+(.5-v)*.5))*(1-smooth(.46,.52,v)); d=max(d,sky*.97)
        val right=smooth(.52,.6,u)*(1-smooth(.55,.6,v)); d+=(.55+.35*(1-u)+.15*(.55-v)-d)*right*.9
        val van=smooth(.48,.52,u)*smooth(.565,.59,v)*(1-smooth(.89,.92,v)); d+=(.2-d)*van
        d+=(.04-d)*smooth(.82,.98,v)*(1-van); return d.coerceIn(0.0,1.0)
    }
}
