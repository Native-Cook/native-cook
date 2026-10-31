package com.ngocdevv.nativecook.blocks.cards.liquidmetalswap

import kotlin.math.*

// Direct transcription of lms-motion.ts, in seconds and dp.
internal object LiquidMetalSwapMotion {
    const val LOOP = 10.0
    const val HOLD = 1.5
    const val R_POOL = 118.0
    fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t-a)/(b-a))
    fun smooth(a: Double, b: Double, x: Double): Double { val u=clamp01((x-a)/(b-a)); return u*u*(3-2*u) }
    fun easeInOut(x: Double) = if (x < 0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    fun springStep(x: Double) = if (x <= 0) 0.0 else 1-exp(-6*x)*(cos(10*x)+0.6*sin(10*x))
    fun settle(d0: Double, v0: Double, tau: Double, omega: Double) = (d0+(v0+omega*d0)*tau)*exp(-omega*tau)
    data class Look(val kind: Double, val sink: Double, val soft: Double, val poolR: Double, val poolH: Double, val wobble: Double, val drops: Double, val gold: Double)
    fun look(m: Double): Look {
        val x=clamp01(m)
        val sink=if (x < 0.5) easeInOut(seg(x,0.03,0.5)) else 1-easeInOut(seg(x,0.5,0.97))
        return Look(if(x < 0.5) 0.0 else 1.0, sink,smooth(0.1,0.55,sink),R_POOL*sink.pow(0.65),5+12*sink,sink,
            if (x > 0.36 && x < 0.64) seg(x,0.36,0.64) else 0.0,smooth(0.4,0.6,x))
    }
    fun sway(t: Double) = 0.3*sin(2*PI*2*t/LOOP)
    object T {
        const val press1=1.0; const val drag0=3.3; const val drag1=3.9
        const val press2=5.2; const val release2=5.8; const val press3=6.6
        const val flip=0.35; const val swapped=1.1
    }
    data class Frame(var fill: Double=0.0, var start: Double=0.0, var m: Double=0.0, var flip: Double=0.0,
        var swapped: Double=0.0, var doneText: Double=0.0, var press: Double=0.0, var yaw: Double=0.0,
        var finger: Double=0.0, var fingerAt: Double=0.0, var fingerX: Double=0.0)
    fun frameAt(t: Double): Frame {
        val done1=T.press1+HOLD; val done3=T.press3+HOLD
        var fill=0.0; var start=0.0
        if(t >= T.press1 && t < done1) fill=(t-T.press1)/HOLD
        else if(t >= done1 && t < T.press2) fill=0.0
        if(t >= done1) start=1.0
        if(t >= T.press2 && t < T.release2) fill=(t-T.press2)/HOLD
        else if(t >= T.release2 && t < T.press3) fill=settle((T.release2-T.press2)/HOLD,1/HOLD,t-T.release2,9.0)
        if(t >= T.press3 && t < done3) fill=(t-T.press3)/HOLD
        if(t >= done3) { start=0.0; fill=0.0 }
        val m=if(start == 0.0) fill else 1-fill
        val flip=easeInOut(seg(t,done1,done1+T.flip))*(1-easeInOut(seg(t,done3,done3+T.flip)))
        val swapped=(if(t >= done1 && t < done1+T.swapped+0.3) 1-seg(t,done1+T.swapped,done1+T.swapped+0.3) else 0.0)+
            (if(t >= done3 && t < done3+T.swapped+0.3) 1-seg(t,done3+T.swapped,done3+T.swapped+0.3) else 0.0)
        val doneText=clamp01(seg(t,done1,done1+0.2)-seg(t,done1+T.swapped,done1+T.swapped+0.3))+
            clamp01(seg(t,done3,done3+0.2)-seg(t,done3+T.swapped,done3+T.swapped+0.3))
        fun pressOf(a: Double,b: Double)=easeOut(seg(t,a,a+0.1))*(1-easeOut(seg(t,b,b+0.15)))
        var yaw=0.0
        if(t >= T.drag0 && t < T.drag1) yaw=1.6*easeOut(seg(t,T.drag0,T.drag1))
        else if(t >= T.drag1) yaw=1.6*(1-springStep((t-T.drag1)/1.1))
        fun near(a: Double,b: Double)=easeOut(seg(t,a-0.22,a))*(1-easeOut(seg(t,b,b+0.22)))
        val onButton=near(T.press1,done1)+near(T.press2,T.release2)+near(T.press3,done3)
        val onCoin=near(T.drag0,T.drag1)
        return Frame(fill,start,m,flip,clamp01(swapped),clamp01(doneText),
            clamp01(pressOf(T.press1,done1)+pressOf(T.press2,T.release2)+pressOf(T.press3,done3)),
            yaw,clamp01(onButton+onCoin),if(onCoin > 0.001) 1.0 else 0.0,easeOut(seg(t,T.drag0,T.drag1)))
    }
    fun bezier(x: Double,x1: Double,y1: Double,x2: Double,y2: Double): Double {
        val p=clamp01(x)
        fun value(t: Double,a: Double,b: Double)=3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
        var lo=0.0; var hi=1.0
        repeat(24) { val t=(lo+hi)/2; if(value(t,x1,x2)<p) lo=t else hi=t }
        return value((lo+hi)/2,y1,y2)
    }
}
