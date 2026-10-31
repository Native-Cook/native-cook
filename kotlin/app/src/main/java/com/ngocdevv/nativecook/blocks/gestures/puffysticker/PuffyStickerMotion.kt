package com.ngocdevv.nativecook.blocks.gestures.puffysticker

import kotlin.math.*

internal object PuffyStickerMotion {
    const val LOOP = 9.6f
    const val MORPH = .65f
    fun seg(t: Float, a: Float, b: Float) = ((t-a)/(b-a)).coerceIn(0f,1f)
    fun ease(x: Float) = if(x<.5f) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun out(x: Float) = 1-(1-x).pow(3)
    fun wobble(t: Float, frequency: Float, decay: Float) = if(t<=0) 0f else sin(2*PI.toFloat()*frequency*t)*exp(-decay*t)
    fun inflate(held: Float) = if(held<=0) 0f else 1-(1+4.2f*held)*exp(-4.2f*held)
    data class Frame(val puff: Float=0f, val shape: Float=0f, val squish: Float=0f, val touch: Float=0f, val tapAt: Int=-1, val tapPress: Float=0f, val flatPress: Float=0f)
    fun frameAt(t: Float): Frame {
        val puff=inflate(min(t,1.7f)-.6f)*(1-ease(seg(t,7.1f,7.75f)))
        var shape=0f
        for(a in floatArrayOf(2.4f,4f,5.6f)) shape+=ease(seg(t,a+.1f,a+.1f+MORPH))
        shape-=3*ease(seg(t,8f,8f+MORPH*1.6f))
        var squish=-.03f*out(seg(t,.6f,.72f))*(1-seg(t,1.7f,1.75f))
        squish+=.09f*wobble(t-1.7f,2.6f,4.2f)
        for(a in floatArrayOf(2.4f,4f,5.6f)) squish+=.05f*wobble(t-(a+.1f+MORPH),3.2f,5.5f)
        squish+=.06f*wobble(t-7.75f,3f,6f)
        val touch=out(seg(t,.48f,.6f))*(1-out(seg(t,1.7f,1.88f)))
        var tapAt=-1; var tapPress=0f
        for((i,a) in floatArrayOf(2.4f,4f,5.6f,7.9f).withIndex()) if(t>=a-.15f && t<a+.35f) {
            tapAt=intArrayOf(1,2,3,0)[i];tapPress=out(seg(t,a,a+.08f))*(1-out(seg(t,a+.12f,a+.3f)))
        }
        return Frame(puff,shape,squish,touch,tapAt,tapPress,out(seg(t,7f,7.08f))*(1-out(seg(t,7.12f,7.3f))))
    }
}

/** Reanimated's duration-spring solver: mass 4, energy threshold 6e-9. */
internal class PuffyStickerChannel(var value: Float=0f) {
    private data class Step(val end: Float,val duration: Float,val damping: Float?=null,val velocity: Float=0f,val easing: (Float)->Float=::puffyStickerDefaultEase)
    private val steps=ArrayDeque<Step>()
    private var from=0.0;private var started=0f;private var stiffness=0.0;private var initialVelocity=0.0
    private var currentVelocity=0.0
    private fun configure(previousVelocity: Double=0.0) {
        val step=steps.firstOrNull() ?: return
        val ratio=step.damping?.toDouble() ?: run { currentVelocity=0.0;return }
        val x0=from-step.end;var v0=step.velocity+previousVelocity
        if((step.end>from && v0<0) || (step.end<from && v0>0)) v0=0.0
        initialVelocity=v0
        if(x0==0.0 && v0==0.0) {stiffness=8000.0;return}
        fun diff(k: Double): Double {
            val t=step.duration*1.5;val omega=sqrt(k/4)*ratio;val e=exp(-omega*t)
            val displacement=(x0+(v0+x0*omega)*t)*e
            val speed=-omega*displacement+(v0+x0*omega)*e
            return (k*displacement*displacement+4*speed*speed)/(k*x0*x0+4*v0*v0)-6e-9
        }
        var lo=2.220446049250313e-16;var hi=8000.0;var k=(lo+hi)/2
        val direction=if(diff(hi)>=diff(lo)) 1 else -1
        for(i in 0 until 100) {if(abs(diff(k))<=6e-12) break;if(diff(k)*direction<0) lo=k else hi=k;k=(lo+hi)/2}
        stiffness=k
    }
    fun at(t: Float): Float {
        val step=steps.firstOrNull() ?: return value
        val elapsed=max(0.0,(t-started).toDouble());var finished=false
        if(step.damping==null) {
            val fraction=min(1f,(elapsed/step.duration).toFloat())
            value=(from+(step.end-from)*step.easing(fraction)).toFloat();finished=fraction>=1
        } else {
            val zeta=step.damping.toDouble();val omega=sqrt(stiffness/4);val x0=from-step.end;val v0=initialVelocity
            val envelope=exp(-zeta*omega*elapsed)
            val displacement: Double
            if(zeta<1) {
                val wd=omega*sqrt(1-zeta*zeta);val a=(v0+zeta*omega*x0)/wd
                displacement=envelope*(sin(wd*elapsed)*a+x0*cos(wd*elapsed))
                currentVelocity=-zeta*omega*displacement+envelope*(cos(wd*elapsed)*(v0+zeta*omega*x0)-wd*x0*sin(wd*elapsed))
            } else {
                displacement=exp(-omega*elapsed)*(x0+(v0+omega*x0)*elapsed)
                currentVelocity=-omega*displacement+exp(-omega*elapsed)*(v0+omega*x0)
            }
            value=(step.end+displacement).toFloat()
            val energy=stiffness*x0*x0+4*v0*v0
            finished=energy==0.0 || (stiffness*displacement*displacement+4*currentVelocity*currentVelocity)/energy<=6e-9
        }
        if(finished) {value=step.end;from=value.toDouble();steps.removeFirst();started=t;currentVelocity=0.0;configure()}
        return value
    }
    fun set(v: Float) {value=v;steps.clear();currentVelocity=0.0}
    fun tween(to: Float,t: Float,duration: Float,easing: (Float)->Float=::puffyStickerDefaultEase) {
        from=at(t).toDouble();steps.clear();started=t;steps.add(Step(to,duration,easing=easing));configure()
    }
    fun spring(to: Float,t: Float,duration: Float,damping: Float,velocity: Float=0f) {
        from=at(t).toDouble();val inherited=currentVelocity;steps.clear();started=t;steps.add(Step(to,duration,damping,velocity));configure(inherited)
    }
    fun sequence(t: Float,points: List<Pair<Float,Float>>,duration: Float,damping: Float) {
        from=at(t).toDouble();steps.clear();started=t;var start=0f
        for((end,v) in points) {steps.add(Step(v,end-start));start=end}
        steps.add(Step(0f,duration,damping));configure()
    }
    fun hop(t: Float) {
        from=at(t).toDouble();steps.clear();started=t
        steps.add(Step(0f,.11f));steps.add(Step(-38f,.22f,easing=::puffyStickerOutEase));steps.add(Step(0f,.52f,.45f));configure()
    }
}

internal fun puffyStickerDefaultEase(x: Float) = if(x<.5f) 2*x*x else 1-(-2*x+2).pow(2)/2
internal fun puffyStickerOutEase(x: Float): Float = puffyStickerBezier(x,.23f,1f,.32f,1f)
internal fun puffyStickerBezier(x: Float,x1: Float,y1: Float,x2: Float,y2: Float): Float {
    var lo=0f;var hi=1f
    repeat(24) {val m=(lo+hi)/2;val u=1-m;val bx=3*u*u*m*x1+3*u*m*m*x2+m*m*m;if(bx<x) lo=m else hi=m}
    val m=(lo+hi)/2;val u=1-m;return 3*u*u*m*y1+3*u*m*m*y2+m*m*m
}

// RN's interactive ease-in-out is cubic-bezier(.77, 0, .175, 1), distinct from loop cubic easing.
internal fun puffyStickerEase(x: Float): Float = puffyStickerBezier(x,.77f,0f,.175f,1f)
