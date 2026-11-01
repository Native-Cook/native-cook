package com.ngocdevv.nativecook.blocks.gestures.evolveholdmorph

import kotlin.math.*

internal data class EvolveHoldMorphFrame(
    var stage: Int = 0, var c: Double = 0.0, var m: Double = 0.0,
    var sq: Double = 0.0, var yaw: Double = 0.0, var burst: Double = -1.0,
    var blink: Double = 0.0, var t: Double = 0.0, var hop: Double = 0.0,
    var hold: Boolean = false,
)
internal object EvolveHoldMorphMotion {
    const val LOOP = 9.0
    val names = listOf("Egg", "Chick", "Bluebird")
    val cta = listOf("Hold to hatch", "Hold to evolve", "Hold for a new egg")
    fun smooth(a: Double, b: Double, v: Double): Double {
        val x = ((v-a)/(b-a)).coerceIn(0.0, 1.0)
        return x*x*(3-2*x)
    }
    fun chargeShape(c: Double) = -0.07*sin(PI*c.coerceIn(0.0,1.0))
    fun at(time: Double): EvolveHoldMorphFrame {
        val t = time % LOOP
        val f = EvolveHoldMorphFrame(t = time)
        var commit = -10.0
        listOf(Triple(0,1.0,2.4),Triple(1,4.4,5.8),Triple(2,7.6,9.0)).forEach { (s,a,b) ->
            if (t >= b) { f.stage = (s+1)%3; commit = b }
            else if (t >= a) { f.stage = s; f.c = (t-a)/(b-a) }
        }
        if (t < 1) commit = 0.0
        f.m = smooth(0.5,1.0,f.c)
        f.sq = chargeShape(f.c) + if(commit >= 0) -0.08*exp(-6*(t-commit))*sin(16*(t-commit)) else 0.0
        f.burst = if(commit > 0 && commit < 9 && f.stage != 0) t-commit else -1.0
        f.yaw = 0.28*sin(time*2*PI/LOOP)
        return f
    }
}
internal class EvolveHoldMorphDriver {
    var now = 0.0
    var stage = 0
    var c = 0.0; var cV = 0.0; var sq = 0.0; var sqV = 0.0
    var hop = 0.0; var hopV = 0.0
    var hold = false
    var lastTouch = -100.0
    var scripted = false
    var loopT = 0.0
    var burstAt = -10.0
    var tick = 0
    fun begin() { hold = true; scripted = false; tick = 0; lastTouch = now }
    fun end() { hold = false; lastTouch = now }
    fun step(wall: Double, auto: Boolean, reduced: Boolean, buzz: (Int)->Unit): EvolveHoldMorphFrame {
        val dt = min(wall,0.05)
        now += wall
        val blinkPh = now % 3.6
        val blink = if(blinkPh < 0.14) sin(blinkPh/0.14*PI) else 0.0
        var yaw = if(reduced) 0.0 else 0.28*sin(now*2*PI/9)
        if(auto && !reduced && !hold && now-lastTouch > 3.5 && (scripted || (c < 0.001 && abs(sq) < 0.004))) {
            if(!scripted) { scripted = true; loopT = when(stage) {0->0.9;1->3.6;else->6.8} }
            loopT = (loopT+wall)%9
            val sc = EvolveHoldMorphMotion.at(loopT)
            stage = sc.stage; c = sc.c; sq = sc.sq; sqV = 0.0
            burstAt = if(sc.burst >= 0) now-sc.burst else -10.0
        } else {
            scripted = false
            if(hold) {
                c += dt/1.4
                val ticks = floor(c*(4+c*8)).toInt()
                if(ticks != tick) { tick = ticks; buzz(0) }
                if(c >= 1) {
                    c = 0.0; cV = 0.0; stage = (stage+1)%3
                    burstAt = if(stage == 0) -10.0 else now
                    hold = false; tick = 0
                    if(!reduced) sqV -= 1.6
                    buzz(2)
                }
            } else if(c > 0) {
                cV += (-90*c-14*cV)*dt
                c += cV*dt
                if(c < 0.001) { c = 0.0; cV = 0.0 }
            }
            val target = if(reduced) 0.0 else EvolveHoldMorphMotion.chargeShape(c)
            sqV += (240*(target-sq)-8*sqV)*dt
            sq += sqV*dt
            if(hold && !reduced) yaw += sin(now*46)*0.05*c
        }
        hopV -= 1600*dt
        hop += hopV*dt
        if(hop < 0) { hop = 0.0; hopV = if(abs(hopV)>80) -hopV*0.3 else 0.0 }
        val m = if(reduced) { if(c>0.95) 1.0 else 0.0 } else EvolveHoldMorphMotion.smooth(0.5,1.0,c)
        return EvolveHoldMorphFrame(stage,c,m,sq,yaw,if(burstAt>0) now-burstAt else -1.0,blink,now,hop,hold)
    }
}
