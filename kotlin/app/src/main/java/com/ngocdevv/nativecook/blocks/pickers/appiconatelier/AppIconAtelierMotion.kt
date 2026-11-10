package com.ngocdevv.nativecook.blocks.pickers.appiconatelier

import kotlin.math.*

internal object AppIconAtelierMotion {
    const val LOOP=10.0
    const val END=1.45
    const val TAU=PI*2
    const val PHASE=PI*6/10
    fun clamp(v:Double)=v.coerceIn(0.0,1.0)
    fun seg(t:Double,a:Double,b:Double)=clamp((t-a)/(b-a))
    fun ease(x:Double)=if(x<.5)4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun out(x:Double)=1-(1-x).pow(3)
    fun arrive(x:Double)=if(x<=0)0.0 else if(x>=1)1.0 else 1-exp(-5.2*x)*cos(6.4*x)*(1-x)
    fun progress(f:Double,d:Double)=clamp(if(d>0)(END-f)/(2*END) else (f+END)/(2*END))
    fun meniscus(f:Double)=1-clamp((abs(f)-1)/.4)
    fun tap(t:Double,at:Double)=out(seg(t,at-.02,at+.08))*(1-out(seg(t,at+.12,at+.26)))
    data class Frame(var a:Int=0,var b:Int=1,var dir:Double=1.0,var front:Double=END,var lean:Double=0.0,var bob:Double=0.0,var press:Double=0.0,var peek:Double=0.0,var land:Double=0.0,var finger:Double=0.0,var fingerAt:Int=0,var fingerX:Double=0.0,var fingerPress:Double=0.0,var phase:Double=0.0,var applied:Int=0,var pop:Double=0.0) {
        val shown get()=if(progress(front,dir)>.5)b else a
        val sel get()=a+(b-a)*ease(progress(front,dir))
    }
    fun at(t:Double):Frame {
        val f=Frame()
        if(t<3.11) {
            val k=seg(t,.95,2.0); val fx=.85-1.7*k*k*(2-k)
            f.front=if(t<2) END+(fx-END)*out(seg(t,.95,1.15)) else -.85-(END-.85)*out(seg(t,2.0,3.11))
            f.lean=-.16*sin(PI*seg(t,.95,2.0))+.05*sin(PI*seg(t,2.0,2.8))
            f.finger=out(seg(t,.8,.95))*(1-out(seg(t,2.0,2.2)));f.fingerX=fx
            f.press=out(seg(t,.9,1.05))*(1-out(seg(t,1.95,2.15)));f.fingerPress=f.press
        } else if(t<4.35) {
            f.a=1;f.b=3;f.front=END-2*END*ease(seg(t,3.45,4.35))
            f.finger=out(seg(t,3.2,3.33))*(1-out(seg(t,3.49,3.65)));f.fingerAt=1;f.fingerX=3.0;f.fingerPress=tap(t,3.35)
        } else if(t<8.05) {
            f.a=3;f.b=3
            f.press=out(seg(t,4.95,5.25))*(1-out(seg(t,5.3,5.55)))
            f.peek=arrive(seg(t,5.3,5.95))*(1-arrive(seg(t,6.95,7.65)))
            f.land=.07*sin(PI*seg(t,5.83,6.23))*(1-seg(t,5.83,6.23))+.05*sin(PI*seg(t,7.51,7.91))*(1-seg(t,7.51,7.91))
            f.finger=out(seg(t,4.8,4.95))*(1-out(seg(t,6.9,7.06)));f.fingerPress=out(seg(t,4.95,5.07))*(1-out(seg(t,6.9,7.02)))
        } else {
            f.a=3;f.b=0;f.dir=-1.0;f.front=-END+2*END*ease(seg(t,8.35,9.25))
            if(t>=9.25){f.a=0;f.b=1;f.dir=1.0;f.front=END}
            f.finger=out(seg(t,8.1,8.23))*(1-out(seg(t,8.39,8.55)));f.fingerAt=1;f.fingerX=0.0;f.fingerPress=tap(t,8.25)
        }
        f.bob=.025*sin(TAU*t/LOOP)*(1-f.peek);f.lean+=.04*sin(2*TAU*t/LOOP);f.phase=PHASE*t%TAU
        return f
    }
}
