package com.ngocdevv.nativecook.blocks.cards.sunrisecocktailpour

import kotlin.math.*

internal object SunriseCocktailPourMotion {
    const val LOOP = 10.0
    const val POUR_START = 1.0 + 0.1
    const val STIR_START = 4.6 + 0.1
    const val SPEED = PI * 6 / LOOP
    data class Frame(
        var level: Double = 0.55, var red: Double = 0.0, var rise: Double = 0.0,
        var mix: Double = 0.0, var stream: Double = 0.0, var swirl: Double = 0.0,
        var shift: Double = 0.0, var phase: Double = 0.0, var step: Int = 0,
        var finger: Double = 0.0, var fingerPress: Double = 0.0,
    )
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp((t-a)/(b-a))
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    fun easeInOut(x: Double) = if(x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun defaultEase(x: Double) = if(x<0.5) 2*x*x else 1-(-2*x+2).pow(2)/2
    fun bezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        fun cubic(t: Double, a: Double, b: Double) = 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
        var lo=0.0; var hi=1.0
        repeat(24) { val m=(lo+hi)/2; if(cubic(m,x1,x2)<clamp(x)) lo=m else hi=m }
        return cubic((lo+hi)/2,y1,y2)
    }
    fun cssOut(x: Double) = bezier(x,0.23,1.0,0.32,1.0)
    fun pour(tau: Double, f: Frame) {
        f.stream=if(tau>0 && tau<1.2) tau/1.2 else 0.0
        f.red=easeOut(clamp((tau-0.4)/1.1)); f.rise=easeInOut(clamp((tau-0.7)/2.2))
        f.level=0.55+(0.62-0.55)*easeOut(clamp((tau-0.3)/0.9))
    }
    fun stir(tau: Double, f: Frame) {
        f.mix=easeInOut(clamp(tau/1.3)); f.swirl=2*PI*1.2*easeOut(clamp(tau/1.6))
    }
    fun at(t: Double): Frame {
        val f=Frame()
        if(t>POUR_START && t<7.6) { pour(t-1.0-0.1,f); f.step=1 }
        if(t>STIR_START && t<7.6) { stir(t-4.6-0.1,f); f.step=2 }
        if(t>=6.9 && t<7.6) f.shift=-3.4*easeInOut(seg(t,6.9,7.6))
        else if(t>=7.6 && t<8.5) { f.shift=3.4*(1-easeOut(seg(t,7.6,8.5))); f.step=0 }
        if(t>=7.6) f.step=0
        for(a in doubleArrayOf(1.0,4.6,6.8)) {
            val finger=easeOut(seg(t,a-0.15,a-0.02))*(1-easeOut(seg(t,a+0.14,a+0.3)))
            if(finger>f.finger) {
                f.finger=finger
                f.fingerPress=easeOut(seg(t,a-0.02,a+0.08))*(1-easeOut(seg(t,a+0.12,a+0.26)))
            }
        }
        f.phase=(SPEED*t)%(2*PI)
        return f
    }
}

internal data class SunriseCocktailPourLayout(val w: Double,val h: Double,val top: Double,val bottom: Double) {
    val ctaTop=h-max(bottom,16.0)-8-56
    val noteTop=ctaTop-16-20
    val recipeTop=noteTop-24-20
    val nameTop=recipeTop-4-44
    val eyebrowTop=nameTop-4-16
    private val room0=top+44+24
    private val room=eyebrowTop-24-room0
    val heroS=minOf(w*0.3,room/2.75,118.0)
    val heroX=w/2
    val heroY=room0+room-heroS*1.32
}
