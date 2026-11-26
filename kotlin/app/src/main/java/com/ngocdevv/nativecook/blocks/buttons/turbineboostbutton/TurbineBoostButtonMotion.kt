package com.ngocdevv.nativecook.blocks.buttons.turbineboostbutton

import kotlin.math.*

/** RN's closed-form loop and inverse-easing live clock. Frame timestamps use milliseconds, phase times seconds. */
internal class TurbineBoostButtonMotion {
    data class Frame(val angle: Double=0.0,val arc: Double=0.0,val boost: Double=0.0,val share: Double=0.0,val held: Boolean=false)
    companion object {
        const val LOOP=5.0
        const val TAU=2*PI
        const val IDLE=.15*TAU
        const val DELTA=8.85*TAU
        const val K=20/19.99875
        fun ease(v: Double)=if(v<.5) 2*v*v else 1-(2-2*v).pow(2)/2
        fun inverse(v: Double)=if(v<.5) sqrt(v/2) else 1-sqrt((1-v)/2)
        fun integral(v: Double)=if(v<=.5) 2*v*v*v/3 else 1.0/12+(v-.5)-(1-(2-2*v).pow(3))/12
        fun shareAt(t: Double)=when { t<.5->0.0; t<2.5->ease(((t-.5)/2).coerceIn(0.0,1.0)); t<3.3->1.0; t<4.8->(1-((t-3.3)/1.5).coerceIn(0.0,1.0)).pow(3); else->0.0 }
        fun turned(t: Double): Double {
            var a=IDLE*t
            if(t>.5) a+=DELTA*2*integral(((t-.5)/2).coerceIn(0.0,1.0))
            if(t>2.5) a+=DELTA*(min(t,3.3)-2.5)
            if(t>3.3) a+=DELTA*1.5*(1-(1-((t-3.3)/1.5).coerceIn(0.0,1.0)).pow(4))/4
            return K*a
        }
        fun wrap(a: Double): Double {
            val value=a-TAU*floor(a/TAU)
            return if(value<1e-10||TAU-value<1e-10) 0.0 else value
        }
        fun look(a: Double,s: Double,held: Boolean)=Frame(wrap(a),min((IDLE+DELTA*s)*.033,2*TAU/22),s*s*(3-2*s),s,held)
        fun frame(t: Double,from: Double=0.0)=look(from+turned(t),shareAt(t),t>=.5&&t<3.3)
    }
    var mode=0; var holding=0; var quarter=0; var ticks=0; var full=false; var once=false
    var last=-1.0; var start=-1.0; var from=0.0; var angle=0.0; var share=0.0; var x=0.0; var y=1.0; var idleTime=0.0
    var out=frame(0.0)
    fun takeover() { if(mode!=0) return; mode=1; angle=out.angle; share=out.share; x=inverse(share); y=1-cbrt(share) }
    fun hold() { takeover(); holding++; idleTime=0.0; if(mode==2) return; x=inverse(share); quarter=min(3,floor(share*4+1e-9).toInt()) }
    fun release() { if(holding<=0) return; holding--; idleTime=0.0; if(holding==0&&mode!=2) y=1-cbrt(share) }
    fun activate() { if(once) return; hold(); once=true; if(share>=1) { once=false; full=true; release() } }
    fun setReduced(reduced: Boolean) {
        if(reduced&&mode!=2) { mode=2; share=0.0; out=Frame(held=holding>0) }
        else if(!reduced&&mode==2) { mode=0; start=-1.0; from=0.0; share=0.0; holding=0; once=false }
    }
    fun step(now: Double,autoPlay: Boolean) {
        val dt=if(last<0) 0.0 else ((now-last)/1000).coerceIn(0.0,.05); last=now
        if(mode==2) {
            val before=share; share=(share+if(holding>0) dt/.4 else -dt/.4).coerceIn(0.0,1.0)
            if(before<1&&share>=1) { full=true; if(once) holding=max(0,holding-1); once=false }
            out=Frame(0.0,0.0,share*share*(3-2*share),share,holding>0); return
        }
        if(mode==0) { if(autoPlay) { if(start<0) start=now; out=frame(((now-start)/1000)%LOOP,from); return }; takeover() }
        if(holding>0&&share<1) {
            x=min(1.0,x+dt/2); share=ease(x)
            val q=min(3,floor(share*4+1e-9).toInt()); if(q>quarter) { ticks+=q-quarter; quarter=q }
            if(x>=1) { share=1.0; full=true; if(once) { once=false; release() } }
        } else if(holding==0&&share>0) { y=min(1.0,y+dt/1.5); share=if(y>=1) 0.0 else (1-y).pow(3) }
        angle=wrap(angle+(IDLE+DELTA*share)*dt); out=look(angle,share,holding>0)
        if(holding>0||share>0) { idleTime=0.0; return }; idleTime+=dt
        if(autoPlay&&idleTime>=2) { mode=0; start=now; from=angle; out=frame(0.0,from) }
    }
}
