package com.ngocdevv.nativecook.blocks.pickers.monsteragepicker

import kotlin.math.*

internal object MonsterAgePickerMotion {
    const val LOOP = 9.0
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun smoother(k: Double): Double { val x = clamp(k); return x*x*x*(x*(x*6-15)+10) }
    fun chomp(u: Double): Double {
        if (u < 0 || u > 0.7) return 0.0
        if (u < 0.16) return smoother(u/0.16)
        if (u < 0.28) return 1.0
        val k = (u-0.28)/0.42
        return (1-k)*(1-k)*cos(k*4.2)
    }
    fun script(time: Double): Pair<Double,Double> {
        val t = time % LOOP
        val off = when {
            t >= 1 && t < 2.6 -> 2+3*smoother((t-1)/1.6)
            t >= 2.6 && t < 6.2 -> 5.0
            t >= 6.2 && t < 7.8 -> 5-3*smoother((t-6.2)/1.6)
            else -> 2.0
        }
        return off to chomp(t-3.4)
    }
    data class Frame(val off: Double = 2.0, val v: Double = 0.0, val jaw: Double = 0.0,
                     val look: Double = 0.0, val blink: Double = 0.0)
}

internal class MonsterAgePickerDriver {
    var now = 0.0
    var off = 2.0
    var v = 0.0
    var target = 2.0
    var off0 = 2.0
    var jawT = -10.0
    var lastTouch = -100.0
    var loopT = 0.0
    var drag = false
    var scripted = false
    var lastTick = 2
    var chosen = -1
    var age = 5
    var frame = MonsterAgePickerMotion.Frame()
    fun step(wall: Double, auto: Boolean, reduced: Boolean, tick: () -> Unit): MonsterAgePickerMotion.Frame {
        val dt = min(wall,0.05)
        now += wall
        val jaw: Double
        if (auto && !reduced && !drag && now-lastTouch > 3.5 && (scripted || abs(off-2)<0.002)) {
            if (!scripted) { scripted = true; loopT = 0.2 }
            val previous = off
            loopT = (loopT+wall)%MonsterAgePickerMotion.LOOP
            val sc = MonsterAgePickerMotion.script(loopT)
            v = (sc.first-previous)/max(dt,0.001)
            off = sc.first; target = round(off); jaw = sc.second
        } else {
            scripted = false
            if (!drag) {
                val k = if (reduced)600.0 else 220.0
                val c = 2*0.9*sqrt(k)
                v += (k*(target-off)-c*v)*dt
                off += v*dt
            }
            jaw = if (reduced) { if (now-jawT<0.25)1.0 else 0.0 } else MonsterAgePickerMotion.chomp(now-jawT)
        }
        val nextTick = round(off).toInt()
        if (nextTick != lastTick) {
            lastTick = nextTick
            if (!scripted)tick()
            age = nextTick.coerceIn(0,11)+3; chosen = -1
        }
        if (jaw > 0.5 && frame.jaw <= 0.5) chosen = round(off).toInt()
        val bp = now%4.2
        frame = MonsterAgePickerMotion.Frame(off,v,jaw,if(reduced)0.0 else (v/6).coerceIn(-1.0,1.0),if(bp<0.14)sin(bp/0.14*PI) else 0.0)
        return frame
    }
    fun pan(translation: Double, velocity: Double) {
        var value = off0-translation/60
        if (value<0)value = -(1-exp(value))*0.5
        if (value>11)value = 11+(1-exp(-(value-11)))*0.5
        off = value; v = -velocity/60; lastTouch = now
    }
    fun release(velocity: Double) {
        drag = false; target = round(off-velocity/60*0.18).coerceIn(0.0,11.0)
        v = -velocity/60; lastTouch = now
    }
    fun tap(x: Double, width: Double) {
        target = round(off+(x-width/2)/60).coerceIn(0.0,11.0)
        scripted = false; lastTouch = now
    }
    fun adjust(d: Int) { target = (target+d).coerceIn(0.0,11.0); scripted = false; lastTouch = now }
    fun chomp() { jawT = now; scripted = false; lastTouch = now }
}
