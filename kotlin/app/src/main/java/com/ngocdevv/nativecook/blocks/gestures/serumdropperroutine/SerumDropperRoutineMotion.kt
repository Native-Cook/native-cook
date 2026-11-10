package com.ngocdevv.nativecook.blocks.gestures.serumdropperroutine

import kotlin.math.*

internal object SerumDropperRoutineMotion {
    const val LOOP = 10.0
    const val GROW = 0.55
    const val NECK = 0.16
    data class Frame(
        var lift: Double = 0.0, var squeeze: Double = 0.0, var soak: Double = 0.0,
        var done: Double = 1.0, var drops: DoubleArray = doubleArrayOf(-1.0,-1.0,-1.0),
        var finger: Double = 0.0, var fingerAt: Int = 0, var press: Double = 0.0,
    )
    fun seg(t: Double,a: Double,b: Double) = ((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun ease(x: Double) = if(x < 0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun out(x: Double) = 1-(1-x).pow(3)
    fun at(t: Double): Frame {
        val f = Frame()
        f.lift = ease(seg(t,1.3,1.9))*(1-ease(seg(t,5.2,5.8)))
        val sq = seg(t,2.0,2.25)*(1-seg(t,4.4,4.7))
        var pulse = 0.0
        doubleArrayOf(2.1,2.9,3.7).forEachIndexed { i,start ->
            val a = t-start
            f.drops[i] = if(a >= 0 && t < 9.9) a else -1.0
            if(a >= 0 && a < GROW+NECK) pulse = max(pulse,sin(PI*a/(GROW+NECK)))
        }
        f.squeeze = (sq*(0.55+0.45*pulse)).coerceIn(0.0,1.0)
        f.soak = seg(t,5.8,9.6)
        f.done = if(t < 0.5) 1.0 else if(t < 4.6) 1-out(seg(t,0.5,0.75)) else out(seg(t,4.6,4.95))
        val f0 = out(seg(t,0.32,0.47))*(1-out(seg(t,0.58,0.74)))
        f.finger = f0; f.press = f0
        val f1 = out(seg(t,1.02,1.17))*(1-out(seg(t,5.2,5.36)))
        if(f1 > f.finger) { f.finger = f1; f.fingerAt = 1; f.press = 0.6+0.4*f.squeeze }
        return f
    }
    fun timing(x: Double,custom: Boolean): Double {
        val p = x.coerceIn(0.0,1.0)
        if(!custom) return if(p < 0.5) 2*p*p else 1-(-2*p+2).pow(2)/2
        var lo = 0.0; var hi = 1.0
        repeat(24) {
            val u = (lo+hi)/2
            val bx = 3*(1-u)*(1-u)*u*0.77+3*(1-u)*u*u*0.175+u*u*u
            if(bx < p) lo=u else hi=u
        }
        val u=(lo+hi)/2
        return 3*(1-u)*u*u+u*u*u
    }
}
internal data class SerumDropperRoutineLayout(val w: Double,val h: Double,private val safeTop: Double,private val safeBottom: Double) {
    val top = max(safeTop,44.0)
    val bottom = max(safeBottom,20.0)
    val sx get() = w/402
    val k get() = (h-top-bottom)/778
    fun y(v: Double) = if(v < 62) v*top/62 else top+(v-62)*k
    val bh get() = 124*sx
    val bw get() = bh*700/1100
    val bx get() = 110*sx
    val btop get() = y(384.0)
    val dh get() = 160*sx
    val dw get() = dh*300/1100
    val dtop get() = btop-52*sx
    val seatTipY get() = dtop+dh*1034/1100
    val floor get() = y(504.0)
    val liftX get() = 165*sx
    fun tip(lift: Double): Pair<Double,Double> {
        val e1=SerumDropperRoutineMotion.ease(min(1.0,lift/0.55))
        val e2=SerumDropperRoutineMotion.ease(max(0.0,(lift-0.55)/0.45))
        return Pair(bx+(liftX-bx)*e2,seatTipY-100*sx*e1+(y(412.0)-(seatTipY-100*sx))*e2)
    }
    companion object {
        val cards = arrayOf(doubleArrayOf(26.0,308.0,165.0,210.0),doubleArrayOf(211.0,378.0,164.0,210.0),doubleArrayOf(26.0,610.0,165.0,200.0),doubleArrayOf(211.0,680.0,164.0,200.0))
        val labels = arrayOf(26.0 to 538.0,210.0 to 314.0,210.0 to 617.0,26.0 to 840.0)
    }
}
