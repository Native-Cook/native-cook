package com.ngocdevv.nativecook.blocks.charts.sproutstats

import kotlin.math.*

internal object SproutStatsMotion {
    const val LOOP = 9.0
    val minutes = intArrayOf(12, 20, 8, 25, 18, 30, 15)
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val colors = intArrayOf(0xFF9AA2, 0xFFB86B, 0xFFD86B, 0x9BE08B, 0x7FD3E8, 0xA7A8FF, 0xE7A6FF)
    data class Frame(val m: Double = 0.0, val scrub: Double = -1.0, val t: Double = 0.0)
    fun clamp(v: Double) = v.coerceIn(0.0, 1.0)
    fun mix(a: Double, b: Double, k: Double) = a+(b-a)*k
    fun smoother(k: Double): Double { val x=clamp(k); return x*x*x*(x*(x*6-15)+10) }
    fun at(t: Double): Frame {
        val p=t%LOOP
        val m=when {
            p>=1.4 && p<2.6 -> smoother((p-1.4)/1.2)
            p>=2.6 && p<6 -> 1.0
            p>=6 && p<7.2 -> 1-smoother((p-6)/1.2)
            else -> 0.0
        }
        return Frame(m, if(p>=3 && p<5.4)(p-3)/2.4*6 else -1.0, t)
    }
}

internal class SproutStatsDriver(private val tick: () -> Unit) {
    var m=0.0; var v=0.0; var target=0.0; var scrub = -1.0
    var now=0.0; var lastTouch = -100.0; var scripted=false; var loopT=0.0
    var drag=false; var horizontal=false; var m0=0.0; var lastDay = -1
    val moving get() = drag || abs(target-m)>0.00001 || abs(v)>0.00001
    fun mode(to: Double) { target=to; scripted=false; lastTouch=now; tick() }
    fun step(wall: Double, auto: Boolean, reduced: Boolean): SproutStatsMotion.Frame {
        now+=wall
        if(auto && !reduced && !drag && now-lastTouch>3.5 && (scripted || (m<0.001 && target==0.0))) {
            if(!scripted) { scripted=true; loopT=0.0 }
            loopT=(loopT+wall)%SproutStatsMotion.LOOP
            val sc=SproutStatsMotion.at(loopT); m=sc.m; target=if(m>0.5)1.0 else 0.0; scrub=sc.scrub
            return SproutStatsMotion.Frame(m,scrub,now)
        }
        scripted=false
        if(!drag || horizontal) {
            val dt=min(wall,0.05); val k=if(reduced)600.0 else 110.0
            v+=(k*(target-m)-2*0.85*sqrt(k)*v)*dt; m+=v*dt
        }
        val d=if(scrub>=0)floor(scrub+0.5).toInt() else -1
        if(d!=lastDay) { lastDay=d; if(d>=0)tick() }
        return SproutStatsMotion.Frame(m,scrub,now)
    }
    fun begin(dx: Double, dy: Double) { drag=true; scripted=false; horizontal=abs(dy)<=abs(dx); m0=m; v=0.0 }
    fun update(dy: Double, x: Double, step: Double) {
        if(horizontal)scrub=((x-30)/step).coerceIn(0.0,6.0)
        else m=SproutStatsMotion.clamp(m0-dy/180)
        lastTouch=now
    }
    fun end(vy: Double) {
        drag=false
        if(horizontal)scrub = -1.0
        else { target=if(vy < -300)1.0 else if(vy>300)0.0 else if(m>0.5)1.0 else 0.0; v = -vy/180 }
        lastTouch=now
    }
}
