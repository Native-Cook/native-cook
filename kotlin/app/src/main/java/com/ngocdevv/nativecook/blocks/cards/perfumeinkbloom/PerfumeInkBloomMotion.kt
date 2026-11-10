package com.ngocdevv.nativecook.blocks.cards.perfumeinkbloom

import kotlin.math.*

internal object PerfumeInkBloomMotion {
    const val LOOP=10.0
    const val NAME="Azure Tide Bloom"
    const val BODY="Bottled by a small house on the coast: bright citrus over a soft vanilla heart, worn close and lasting into the evening."
    val notes=listOf("bergamot","sea salt","vanilla")
    data class Layout(val w: Float,val h: Float,val safeTop: Float,val safeBottom: Float) {
        val top=max(safeTop,44f); val bottom=max(safeBottom,20f)
        val sx=w/402; val k=(h-top-bottom)/778
        fun y(v: Float)=if(v<62)v*top/62 else top+(v-62)*k
        val cx=163*sx; val cy=y(300f)
        val capX=cx+sin(.375f)*138*sx; val capY=cy-cos(.375f)*138*sx
    }
    data class Frame(var press: Double=0.0,var age: Double=-1.0,var condense: Double=0.0,var evaporate: Double=0.0,var tilt: Double=0.0,var slosh: Double=0.0,var finger: Double=0.0,var fingerAt: Int=0,var fingerU: Double=0.0,var press2: Double=0.0)
    fun clamp(v: Double)=v.coerceIn(0.0,1.0)
    fun seg(t: Double,a: Double,b: Double)=clamp((t-a)/(b-a))
    fun ease(x: Double)=if(x<.5)4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun out(x: Double)=1-(1-x).pow(3)
    fun timing(x: Double)=if(x<.5)2*x*x else 1-(-2*x+2).pow(2)/2
    fun leanAt(t: Double): Double {
        if(t<7.1)return 0.0
        if(t<7.7)return -.22*ease(seg(t,7.1,7.7))
        if(t<8.4)return -.22+.4*ease(seg(t,7.7,8.4))
        val u=t-8.4; return .18*exp(-3.2*u)*cos(7*u)
    }
    val slosh=run {
        val values=DoubleArray(2400); val dt=1.0/240
        var x=0.0; var v=0.0
        repeat(2) { pass -> repeat(2400) { i ->
            val t=i*dt; val a=(leanAt(t+dt)-2*leanAt(t)+leanAt(max(0.0,t-dt)))/(dt*dt)
            v+=(-81*x-2*.16*9*v-a*.06)*dt; x+=v*dt
            if(pass==1)values[i]=x
        } }; values
    }
    fun frameAt(t: Double): Frame {
        val s=Frame()
        s.press=out(seg(t,.75,.95))*(1-out(seg(t,.95,1.2)))
        s.age=if(t>=.75&&t<7.2)t-.75 else -1.0
        s.condense=ease(seg(t,1.9,3.2)); s.evaporate=seg(t,5.6,6.8)
        s.tilt=leanAt(t); s.slosh=slosh[floor(t*240+.5).toInt().coerceIn(0,2399)]
        val f0=out(seg(t,.55,.71))*(1-out(seg(t,1.2,1.36)))
        if(f0>s.finger) { s.finger=f0; s.press2=s.press }
        val f1=out(seg(t,6.88,7.06))*(1-out(seg(t,8.4,8.56)))
        if(f1>s.finger) { s.finger=f1; s.fingerAt=1; s.fingerU=s.tilt/.22; s.press2=f1 }
        return s
    }
}
