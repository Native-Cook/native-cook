package com.ngocdevv.nativecook.blocks.cards.snowglobegift

import kotlin.math.*

internal object SnowGlobeGiftMotion {
    const val LOOP = 10.0
    fun seg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    fun easeInOut(x: Double) = if (x < 0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    data class Frame(
        var wobble: Double = 0.0, var swirl: Double = 0.0, var spin: Double = 0.0,
        var lid: Double = 0.0, var glow: Double = 0.0, var amount: Double = 0.0,
        var finger: Double = 0.0, var fingerAt: Double = 0.0, var fingerX: Double = 0.0, var fingerPress: Double = 0.0,
    )
    fun frameAt(t: Double): Frame {
        val f = Frame()
        if (t >= 0.9 && t < 3.3) {
            val tau = t-0.9
            val drive = 1-seg(t,0.9,1.6)
            f.wobble = 0.16*sin(tau*2*PI*2.6)*(0.4+0.6*drive)*exp(-1.6*max(0.0,t-1.6))
            f.wobble *= 1-seg(t,2.8,3.3)
        }
        f.swirl = easeOut(seg(t,1.0,1.6))*(1-easeInOut(seg(t,1.6,5.2)))
        val k = seg(t,0.9,5.2)
        f.spin = 9*(1-(1-k)*(1-k))
        f.lid = easeOut(seg(t,5.7,6.2))*(1-easeInOut(seg(t,8.3,8.9)))
        f.glow = f.lid
        f.amount = seg(t,5.9,6.3)*(1-seg(t,8.1,8.4))
        if (t < 3) {
            f.finger = easeOut(seg(t,0.75,0.9))*(1-easeOut(seg(t,1.6,1.78)))
            f.fingerX = 0.5*sin((t-0.9)*2*PI*2.6)*(if (t > 0.9) 1 else 0)
            f.fingerPress = f.finger
        } else {
            f.fingerAt = 1.0
            f.finger = easeOut(seg(t,5.45,5.58))*(1-easeOut(seg(t,5.74,5.9)))
            f.fingerPress = easeOut(seg(t,5.58,5.68))*(1-easeOut(seg(t,5.72,5.86)))
        }
        return f
    }
    fun bezier(p: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        fun b(t: Double,a: Double,c: Double) = 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*c+t*t*t
        var lo = 0.0; var hi = 1.0
        repeat(24) { val m = (lo+hi)/2; if (b(m,x1,x2) < p) lo = m else hi = m }
        return b((lo+hi)/2,y1,y2)
    }
}

internal data class SnowGlobeGiftLayout(val w: Float,val h: Float,val top: Float,val bottom: Float) {
    val ctaTop = h-max(bottom,16f)-8f-56f
    val noteTop = ctaTop-32f-44f
    val titleTop = noteTop-8f-42f
    val fromTop = titleTop-4f-18f
    private val room0 = top+44f+16f
    private val room = fromTop-24f-room0
    val heroS = minOf(w*0.34f,room/2.45f,132f)
    val heroX = w/2f
    val heroY = room0+(room-heroS*2.35f)/2f+heroS
    val amountY = heroY-heroS*0.64f
}
