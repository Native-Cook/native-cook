package com.ngocdevv.nativecook.blocks.gestures.clayreactionmorph

import kotlin.math.*

internal object ClayReactionMorphMotion {
    const val LOOP = 11.0
    const val REST_T = 9.4
    fun seg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun smooth(a: Double, b: Double, x: Double): Double { val u=seg(x,a,b); return u*u*(3-2*u) }
    fun ease(x: Double) = if(x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun out(x: Double) = 1-(1-x).pow(3)
    fun back(x: Double): Double { val u=x.coerceIn(0.0,1.0)-1; return 1+2.25*u*u*u+1.25*u*u }
    fun land(u: Double) = if(u<=0 || u>=0.9) 0.0 else 0.85*exp(-u*8)*sin(u*24)
    private val slides = arrayOf(doubleArrayOf(1.75,2.35,1.0,2.0),doubleArrayOf(2.75,3.35,2.0,3.0),doubleArrayOf(3.75,5.0,3.0,0.0),doubleArrayOf(5.4,6.2,0.0,2.0),doubleArrayOf(8.75,9.35,2.0,1.0))
    fun reaction(t: Double): Double { var r=1.0; for(s in slides) if(t>=s[0]) r=s[2]+(s[3]-s[2])*ease(seg(t,s[0],s[1])); return r }
    private fun lift(t: Double,a: Double,b: Double) = if(t<a) 0.0 else if(t<b) back(seg(t,a,a+0.6)) else 1-ease(seg(t,b,b+0.6))
    fun frameAt(loopTime: Double): ClayReactionMorphFrame {
        val t=((loopTime+1.6)%LOOP+LOOP)%LOOP
        val r=reaction(t)
        fun up(a: Double,b: Double) = ease(seg(t,a,a+0.2))*(1-ease(seg(t,b,b+0.3)))
        fun pr(a: Double,b: Double) = out(seg(t,a,a+0.12))*(1-out(seg(t,b,b+0.25)))
        fun pop(a: Double) = -0.35*sin(PI*seg(t,a,a+0.35))
        fun near(a: Double,b: Double) = out(seg(t,a-0.25,a))*(1-out(seg(t,b,b+0.2)))
        return ClayReactionMorphFrame(lift(t,1.0,6.55)+lift(t,8.05,9.6),r,up(1.0,6.55)+up(8.05,9.6),pr(0.7,1.0)+pr(7.75,8.05),pop(1.0)+pop(8.05)+land(t-7.15)+land(t-10.2),((reaction(t+0.03)-reaction(t-0.03))/0.06*0.32).coerceIn(-1.0,1.0),near(0.7,6.55)+near(7.75,9.6),if(t<7.45) r-1 else r-2)
    }
}
internal data class ClayReactionMorphFrame(val lift: Double=0.0,val r: Double=1.0,val hold: Double=0.0,val press: Double=0.0,val squash: Double=0.0,val lean: Double=0.0,val finger: Double=0.0,val fingerDx: Double=0.0)
