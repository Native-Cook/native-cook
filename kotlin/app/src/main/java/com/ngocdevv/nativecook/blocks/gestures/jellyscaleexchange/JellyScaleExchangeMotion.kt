package com.ngocdevv.nativecook.blocks.gestures.jellyscaleexchange

import kotlin.math.*

internal object JellyScaleExchangeMotion {
    const val LOOP = 10.0
    const val BEANS = 16.0
    const val POUR_RATE = 6.0
    const val POINTS_PER_BEAN = 5
    const val TILT = 0.55
    fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t-a)/(b-a))
    fun smooth(a: Double, b: Double, x: Double): Double { val u=seg(x,a,b); return u*u*(3-2*u) }
    fun easeInOut(x: Double) = if(x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    fun wobbleAt(u: Double) = if(u<=0 || u>=1) 0.0 else exp(-u*5)*sin(u*26)
    fun sinkOf(pour: Double, coin: Double): Double {
        val land=pour-floor(pour)
        val nudge=if(pour>0.5) 0.012*exp(-land*9)*sin(land*30) else 0.0
        return 0.03*min(pour,BEANS)/BEANS*(1-smooth(0.3,0.8,coin))+nudge*(1-coin)
    }
    data class Frame(val pour: Double=0.0,val melt: Double=0.0,val coin: Double=0.0,val spin: Double=0.0,val fly: Double=0.0,
        val wobbleU: Double=1.0,val plus: Double=1.0,val pressPour: Double=0.0,val pressSwap: Double=0.0,val finger: Double=0.0,val fingerAt: Double=0.0)
    fun frameAt(t: Double): Frame {
        val gone=t>=7.6+0.05
        fun press(a: Double,b: Double)=easeOut(seg(t,a,a+0.1))*(1-easeOut(seg(t,b,b+0.15)))
        fun tap(a: Double)=easeOut(seg(t,a-0.08,a))*(1-easeOut(seg(t,a+0.06,a+0.2)))
        fun near(a: Double,b: Double)=easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.2)))
        val onPour=near(0.8,3.65); val onSwap=near(4.25,4.45)
        return Frame(if(gone) 0.0 else BEANS*seg(t,0.9,0.9+BEANS/POUR_RATE),
            if(gone) 0.0 else easeInOut(seg(t,4.4,5.5)),if(gone) 0.0 else easeInOut(seg(t,5.5,6.3)),
            if(gone) 0.0 else 2*PI*easeInOut(seg(t,5.8,6.8)),if(gone) 0.0 else easeInOut(seg(t,6.9,7.6)),
            if(t<7.6) 1.0 else seg(t,7.6,7.6+0.7),if(t<7.6) 1.0 else seg(t,7.6,8.5),
            press(0.8,3.65),tap(4.3),clamp01(onPour+onSwap),if(onPour>0.001) 0.0 else 1.0)
    }
    data class Layout(val w: Double,val h: Double,val top: Double,val bottom: Double) {
        val ppu=round(w*0.4)
        val stageY=top+96+round((h-top-bottom-96-120)*0.5)
        val buttonsTop=h-max(bottom,16.0)-8-56
        val lcdY=stageY+(0.6*cos(TILT)+0.78*sin(TILT))*ppu
        val lcdW=ppu*0.92; val lcdH=ppu*0.25
        val pillX=w-80; val pillY=top+30
    }
}
