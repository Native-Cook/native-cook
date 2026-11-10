package com.ngocdevv.nativecook.blocks.cards.sapphirewatchcrown

import kotlin.math.*

internal object SapphireWatchCrownMotion {
    const val LOOP = 10.0
    const val REST_HOURS = 10.0 + 8.0 / 60
    fun seg(t: Double,a: Double,b: Double) = ((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun out(x: Double) = 1-(1-x).pow(3)
    fun inOut(x: Double) = if(x<.5)4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun mod(x: Double,p: Double) = ((x%p)+p)%p
    fun bezier(x: Double): Double {
        fun at(p: Double,a: Double,b: Double) = 3*(1-p)*(1-p)*p*a+3*(1-p)*p*p*b+p*p*p
        var lo=0.0; var hi=1.0
        repeat(24) { val p=(lo+hi)/2; if(at(p,.77,.175)<x)lo=p else hi=p }
        return at((lo+hi)/2,0.0,1.0)
    }
    data class Frame(var hours: Double=REST_HOURS,var crown: Double=0.0,var flip: Double=0.0,
        var metalFrom: Int=1,var metalTo: Int=1,var metalK: Double=0.0,var finger: Double=0.0,
        var fingerAt: Int=0,var fingerU: Double=0.0,var swatch: Int=1,var press: Double=0.0) {
        val selectedMetal get() = if(metalK>.5)metalTo else metalFrom
    }
    fun frameAt(t: Double): Frame {
        val f=Frame()
        val w=inOut(seg(t,.7,2.4)); f.hours=REST_HOURS+12*w; f.crown=w*12*PI
        f.flip=inOut(seg(t,3.1,3.9))*(1-inOut(seg(t,6.4,7.2)))
        if(t>=7.8 && t<9.2) { f.metalFrom=1; f.metalTo=2; f.metalK=inOut(seg(t,7.8,8.4)) }
        else if(t>=9.2) { f.metalFrom=2; f.metalTo=1; f.metalK=inOut(seg(t,9.2,9.8)) }
        f.swatch=f.selectedMetal
        val a=out(seg(t,.5,.66))*(1-out(seg(t,2.4,2.56)))
        if(a>f.finger) { f.finger=a; f.fingerU=w; f.press=a }
        val b=out(seg(t,3.1-.22,3.1-.05))*(1-out(seg(t,3.1+.2,3.1+.36)))
        if(b>f.finger) { f.finger=b; f.fingerAt=1; f.fingerU=seg(t,3.0,3.35); f.press=b }
        val c=out(seg(t,6.4-.22,6.4-.05))*(1-out(seg(t,6.4+.2,6.4+.36)))
        if(c>f.finger) { f.finger=c; f.fingerAt=1; f.fingerU=seg(t,6.3,6.65); f.press=c }
        val g=out(seg(t,7.8-.2,7.8-.04))*(1-out(seg(t,7.8+.1,7.8+.26)))
        if(g>f.finger) { f.finger=g; f.fingerAt=2; f.swatch=2; f.press=g }
        val r=out(seg(t,9.2-.2,9.2-.04))*(1-out(seg(t,9.2+.1,9.2+.26)))
        if(r>f.finger) { f.finger=r; f.fingerAt=3; f.press=r }
        return f
    }
}

internal data class SapphireWatchCrownLayout(val w: Double,val h: Double,val safeTop: Double,val safeBottom: Double) {
    val top=max(safeTop,44.0)
    val bottom=max(safeBottom,20.0)
    val sx=w/402
    val k=(h-top-bottom)/778
    fun y(v: Double) = if(v<62)v*top/62 else top+(v-62)*k
    val cx=195*sx; val cy=y(345.0); val r=84*sx
    val crownX=cx+.749*r; val crownY=cy-.655*r
    companion object {
        val swatches=doubleArrayOf(35.0,73.0,112.0,145.0)
        val sizes=doubleArrayOf(236.0,273.0,311.0,354.0)
    }
}
