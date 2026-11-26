package com.ngocdevv.nativecook.blocks.playground.spinningtoptoy

import kotlin.math.*

internal data class SpinningTopToyFrame(
    var x: Double=203.7,var y: Double=603.5,var angle: Double=0.0,var spin: Double=0.0,
    var speed: Double=0.0,var glow: Double=0.35,var alpha: Double=1.0,var fall: Double=0.0,
    var trail: FloatArray=FloatArray(72),var seconds: Double=0.0,var down: Boolean=false,
    var pill: Boolean=false,var best: Double=5.4,var bestPop: Double=1.0,
)

// Same state machine and analytic poses as the committed RN reference (stt-motion.ts).
internal class SpinningTopToyMotion {
    companion object {
        const val LOOP=7.6
        val fallSpeed=70*exp(-0.32*5.2)
        fun clamp(x: Double)=x.coerceIn(0.0,1.0)
        fun seg(t: Double,a: Double,b: Double)=clamp((t-a)/(b-a))
        fun io(x: Double)=if(x<0.5) 2*x*x else 1-(-2*x+2).pow(2)/2
        fun durationOf(w: Double)=ln(max(w,fallSpeed)/fallSpeed)/0.32
        fun speed(t: Double,w: Double,d: Double): Double {
            if(t<0) return 0.0
            if(t<d) return w*exp(-0.32*t)
            return if(t<d+0.35) fallSpeed*(1-seg(t,d,d+0.35)) else 0.0
        }
        fun spin(t: Double,w: Double,d: Double): Double {
            if(t<0) return 0.0
            var a=w/0.32*(1-exp(-0.32*min(t,d)))
            if(t>d) { val u=min(t,d+0.35)-d; a+=fallSpeed*(u-u*u/0.7) }
            return a
        }
        fun wander(t: Double,d: Double): Pair<Double,Double> {
            val tt=t.coerceIn(0.0,d); val r=14+58*clamp(t/d); val a=1.3*tt+0.9
            return Pair(r*(cos(a)-cos(0.9)),0.28*r*(sin(a)-sin(0.9)))
        }
        fun pose(t: Double,d: Double,still: Boolean): DoubleArray {
            if(t<0 || still) return doubleArrayOf(203.7,603.5,0.0)
            val (x,y)=wander(min(t,d),d)
            if(t<d) {
                val m=0.035+0.3*clamp(t/d).pow(3); val psi=2*PI*(1.1*t+0.55*t*t/d)
                return doubleArrayOf(203.7+x,603.5+y,m*sin(psi))
            }
            val a0=(0.035+0.3*((d-0.001)/d).pow(3))*sin(2*PI*(1.1*d+0.55*d))
            val f=seg(t,d,d+0.42)
            var a=a0+(-0.745-a0)*f*f
            if(t>d+0.42) { val u=t-d-0.42; a=-0.745+0.07*exp(-u*7)*cos(u*24) }
            return doubleArrayOf(203.7+x+46*io(f),603.5+y,a)
        }
        fun glow(t: Double,w: Double,d: Double): Double {
            if(t<0) return 0.35
            if(t<d) return min(1.0,0.35+0.65*min(1.0,t/0.15)*(0.55+0.45*speed(t,w,d)/70))
            if(t<d+0.42) return 0.7
            return 0.7-0.52*io(seg(t,d+0.42,d+1.02))
        }
        fun run(t: Double,w: Double,d: Double,still: Boolean,alpha: Double=1.0,trailFade: Double=1.0,nudge: Double=0.0): SpinningTopToyFrame {
            val p=pose(t,d,still)
            val f=SpinningTopToyFrame(x=p[0],y=p[1],angle=if(still) 0.0 else p[2]+if(t>=0 && t<d) nudge else 0.0,
                spin=if(still) 0.0 else spin(t,w,d),speed=if(still) 0.0 else speed(t,w,d),glow=glow(t,w,d),alpha=alpha,
                seconds=if(t<0) 0.0 else min(t,d),down=t>=d)
            f.fall=if(t>=d && !still) clamp(f.angle / -0.745)*alpha else 0.0
            for(i in 0 until 24) {
                val ta=t-(i+1)*0.09
                if(ta>=0 && trailFade>0 && !still) {
                    val pa=pose(min(ta,d),d,false)
                    f.trail[i*3]=pa[0].toFloat(); f.trail[i*3+1]=pa[1].toFloat()
                    f.trail[i*3+2]=((1-i/24.0)*min(1.0,speed(ta,w,d)/20)*trailFade).toFloat()
                }
            }; return f
        }
        fun loopFrame(t: Double): SpinningTopToyFrame {
            val tr=if(t>=7.35) 0.0 else t
            val alpha=if(t<7) 1.0 else if(t<7.25) 1-seg(t,7.0,7.25) else seg(t,7.35,7.6)
            val fade=if(tr>=7.25) 0.0 else if(tr<7) 1.0 else 1-seg(tr,7.0,7.25)
            return run(tr-0.4,70.0,5.2,false,alpha,fade).apply { down=tr>=0.4+5.2 && tr<7.25; pill=t>0.25 && t<0.45 }
        }
    }
    var mode=0; var phase=0; var last = -1.0; var start = -1.0; var still=false
    var w0=70.0; var duration=5.2; var tau = -1.0; var nudge=0.0; var resetTime=0.0; var flickAfter=0.0
    var pill=false; var best=5.4; var bestAge=1000.0; var touches=0; var idle=0.0
    var flicks=0; var lands=0; var landed=false; var frame=SpinningTopToyFrame()
    fun takeOver() {
        if(mode!=0) return
        val t=if(start<0) 0.0 else ((last-start)/1000)%LOOP
        mode=1; w0=70.0; duration=5.2; nudge=0.0; landed=false
        if(t<0.4 || t>=7.35) { phase=0; tau = -1.0 }
        else if(t<7) { phase=1; tau=t-0.4; landed=tau>=duration+0.42 }
        else { phase=2; tau=min(t,7.25)-0.4; resetTime=t-7 }
    }
    fun flick(w: Double) { phase=1; w0=w.coerceIn(40.0,90.0); duration=durationOf(w0); tau=0.0; nudge=0.0; landed=false; flicks++ }
    fun spin(speed: Double=0.0,onTop: Boolean=false) {
        takeOver(); idle=0.0
        val w=if(speed>0) speed*0.05 else 70.0
        if(phase==0) flick(w)
        else if(phase==1 && tau<duration) { if(onTop && speed<=0) nudge+=0.05 }
        else if(phase==1) { phase=2; resetTime=0.0; flickAfter=w }
        else flickAfter=w
    }
    fun touchDown(onPill: Boolean=false) { takeOver(); touches++; idle=0.0; if(onPill) pill=true }
    fun touchUp() { touches=max(0,touches-1); pill=false; idle=0.0 }
    fun liveFrame(): SpinningTopToyFrame {
        val f=if(phase==2) {
            val r=resetTime; val standing=r>=0.35
            val alpha=if(r<0.25) 1-r/0.25 else if(standing) clamp((r-0.35)/0.25) else 0.0
            run(if(standing) -1.0 else tau,w0,duration,still,alpha,if(standing) 0.0 else 1-clamp(r/0.25)).apply { down = !standing }
        } else run(tau,w0,duration,still,1.0,1.0,nudge)
        f.pill=pill; f.best=best; f.bestPop=if(bestAge<0.3) 1+0.12*sin(PI*bestAge/0.3) else 1.0
        return f
    }
    fun step(secondsNow: Double,auto: Boolean) {
        val now=secondsNow*1000
        val dt=if(last<0) 0.0 else ((now-last)/1000).coerceIn(0.0,0.05); last=now
        if(mode==0) {
            if(auto && !still) { if(start<0) start=now; frame=loopFrame(((now-start)/1000)%LOOP); return }
            takeOver()
        }
        bestAge+=dt; nudge*=exp(-3*dt)
        if(phase==1) {
            tau+=dt
            if(!landed && tau>=duration+0.42) {
                landed=true; lands++
                if(duration>best) { best=round(duration*10)/10; bestAge=0.0 }
            }
        } else if(phase==2) {
            resetTime+=dt
            if(resetTime>=0.6) {
                phase=0; tau = -1.0
                if(flickAfter>0) { val w=flickAfter; flickAfter=0.0; flick(w) }
                else if(auto && !still) { mode=0; start=now; frame=loopFrame(0.0); return }
            }
        }
        frame=liveFrame()
        val settled=phase==0 || (phase==1 && tau>=duration+1.02)
        if(touches>0 || !settled) { idle=0.0; return }
        idle+=dt
        if(!auto || idle<2) return
        if(phase==1) { phase=2; resetTime=0.0 }
        else if(!still) { mode=0; start=now; frame=loopFrame(0.0) }
    }
    fun setReduced(value: Boolean) {
        if(value==still) return
        still=value
        if(value) takeOver() else if(mode==1) idle=2.0
    }
}
