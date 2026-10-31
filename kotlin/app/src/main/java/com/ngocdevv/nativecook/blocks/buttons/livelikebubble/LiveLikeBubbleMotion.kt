package com.ngocdevv.nativecook.blocks.buttons.livelikebubble

import kotlin.math.*

/** Direct transcription of llb-motion.ts; seconds and scene units. */
internal object LiveLikeBubbleMotion {
    const val LOOP = 9.0
    const val FLIGHT = 0.8
    const val SWALLOW = 0.16
    const val SLOTS = 6
    const val BASE = 11
    const val CENTRE_Y = 1.15
    val THROWS = doubleArrayOf(1.0,1.45,1.9,3.0,3.22,3.44,3.66,3.88,4.1,4.32,4.54,4.76,4.98,5.2)
    val LANES = doubleArrayOf(-0.25,0.1,0.3,0.0,-0.15,0.2,-0.08,0.14,-0.22,0.05,0.25,-0.12,0.18,-0.03)
    val DECAY = doubleArrayOf(6.4,8.6)
    data class Frame(val count: Int, val swell: Double, val press: Double, val finger: Double, val fingerX: Double = 0.0)
    data class Point(val x: Double, val y: Double, val z: Double)
    fun clamp01(v: Double) = v.coerceIn(0.0,1.0)
    fun seg(t: Double,a: Double,b: Double) = clamp01((t-a)/(b-a))
    fun smooth(a: Double,b: Double,x: Double): Double { val u = seg(x,a,b); return u*u*(3-2*u) }
    fun easeOut(x: Double): Double { val u = 1-x; return 1-u*u*u }
    fun targetOf(i: Int): Point {
        val a = (i*2.39996)%(2*PI)
        return Point(0.15+0.75*cos(a),CENTRE_Y+0.45+0.15*sin(a),0.1)
    }
    fun heartAt(now: Double,start: Double,lane: Double,i: Int): FloatArray {
        val u = (now-start)/FLIGHT
        if (u < 0 || u > 1+SWALLOW/FLIGHT) return FloatArray(4)
        val tg = targetOf(i)
        val k = clamp01(u); val e = k*k*(1.6-0.6*k); val v = 1-e
        val sx = lane; val sy = -1.25; val sz = 2.3
        val cx = (sx+tg.x)/2; val cy = max(sy,tg.y)+0.9; val cz = 0.8
        val x = v*v*sx+2*v*e*cx+e*e*tg.x
        val y = v*v*sy+2*v*e*cy+e*e*tg.y
        val z = v*v*sz+2*v*e*cz+e*e*tg.z
        val w = clamp01((now-start-FLIGHT)/SWALLOW)
        val size = (0.22+0.04*sin(i.toDouble()))*smooth(0.0,0.12,u)*(1-w)
        return floatArrayOf((x*(1-w*0.5)).toFloat(),(y+(CENTRE_Y-y)*w*0.5).toFloat(),(z*(1-w)).toFloat(),size.toFloat())
    }
    fun pulse(age: Double) = if (age <= 0 || age >= 0.6) 0.0 else exp(-age*9)*sin(age*22+0.3)
    fun countAt(t: Double): Int {
        val n = THROWS.count { t >= it+FLIGHT+SWALLOW }
        val fall = floor(THROWS.size*smooth(DECAY[0],DECAY[1],t)+1e-6).toInt()
        return BASE+n-fall
    }
    fun frameAt(t: Double): Frame {
        val sw = THROWS.sumOf { pulse(t-it-FLIGHT-SWALLOW*0.5) }
        fun tap(a: Double) = easeOut(seg(t,a-0.06,a))*(1-easeOut(seg(t,a+0.05,a+0.16)))
        var press = tap(THROWS[0])+tap(THROWS[1])+tap(THROWS[2])
        val last = THROWS.last()
        press += easeOut(seg(t,THROWS[3]-0.06,THROWS[3]))*(1-easeOut(seg(t,last+0.05,last+0.2)))
        val near = easeOut(seg(t,THROWS[0]-0.3,THROWS[0]-0.05))*(1-easeOut(seg(t,last+0.1,last+0.35)))
        return Frame(countAt(t),sw.coerceIn(-1.0,1.0),clamp01(press),near)
    }
    fun heartsAt(t: Double): Array<FloatArray> {
        val hs = Array(SLOTS) { FloatArray(4) }
        for (i in THROWS.indices) if (t >= THROWS[i] && t <= THROWS[i]+FLIGHT+SWALLOW) {
            hs[i%SLOTS] = heartAt(t,THROWS[i],LANES[i],i)
        }
        return hs
    }
}
