package com.ngocdevv.nativecook.blocks.gestures.serumdropperroutine

import kotlin.math.*

internal class SerumDropperRoutineDriver(private val auto: Boolean, val ticks: BooleanArray, private val buzz: (Int)->Unit) {
    data class Tween(val from: Double,val to: Double,val start: Double,val duration: Double,val custom: Boolean) {
        fun value(now: Double) = from+(to-from)*SerumDropperRoutineMotion.timing((now-start)/duration,custom)
    }
    var frame = SerumDropperRoutineMotion.Frame()
    init { ticks[0] = true }
    var now = 0.0
    private var autoStart=0.0
    private var lastTouch=-100.0
    private var lastDrop=-100.0
    private var manual=!auto
    private var holding=false
    private var nextDrop=0
    private var dropAt=doubleArrayOf(-100.0,-100.0,-100.0)
    private val tweens=mutableMapOf<String,Tween>()
    val moving get() = (auto && !manual) || holding || tweens.isNotEmpty() || nextDrop>0 || frame.drops.any { it>=0 } || (auto && manual && frame.done>0.999)
    private fun animate(key: String,from: Double,to: Double,duration: Double,custom: Boolean=false) {
        tweens[key]=Tween(from,to,now,duration,custom)
    }
    private fun takeOver() {
        lastTouch=now
        if(manual) return
        manual=true
        animate("finger",frame.finger,0.0,0.15)
        frame.press=0.0
        dropAt=frame.drops.map { if(it>=0) now-it else -100.0 }.toDoubleArray()
        lastDrop=now
        if(!holding) animate("lift",frame.lift,0.0,0.5,true)
        animate("squeeze",frame.squeeze,0.0,0.2)
    }
    fun begin(reduced: Boolean) {
        takeOver(); holding=true
        if(frame.drops.none { it>=0 }) nextDrop=0
        animate("lift",frame.lift,1.0,if(reduced) 0.2 else 0.65,true)
        lastDrop=now+if(reduced) 0.0 else 0.2
        buzz(0)
    }
    fun end(reduced: Boolean) {
        lastTouch=now; holding=false
        animate("squeeze",frame.squeeze,0.0,0.2)
        animate("lift",frame.lift,0.0,if(reduced) 0.2 else 0.6,true)
    }
    fun toggle(i: Int) {
        buzz(0); ticks[i]=!ticks[i]
        if(i==0) { takeOver(); animate("done",frame.done,if(frame.done<0.5)1.0 else 0.0,0.3) }
    }
    fun step(dt: Double) {
        now+=dt
        if(!manual && auto) frame=SerumDropperRoutineMotion.at((now-autoStart)%10)
        else {
            tweens.forEach { (key,tween) ->
                val v=tween.value(now)
                when(key) { "lift"->frame.lift=v; "squeeze"->frame.squeeze=v; "done"->frame.done=v; "finger"->frame.finger=v }
            }
            tweens.entries.removeAll { now>=it.value.start+it.value.duration }
            if(holding && frame.lift>0.98 && nextDrop<3 && now-lastDrop>0.8) {
                dropAt[nextDrop]=now; nextDrop++; lastDrop=now; frame.soak=0.0
            }
            val before=frame.drops.count { it>0.96 }
            frame.drops=dropAt.map { if(it> -50) now-it else -1.0 }.toDoubleArray()
            var pulse=0.0
            frame.drops.filter { it>=0 && it<0.71 }.forEach { pulse=max(pulse,sin(PI*it/0.71)) }
            if(holding && frame.lift>0.98) { frame.squeeze=0.55+0.45*pulse; tweens.remove("squeeze") }
            val last=frame.drops.filter { it>=0 }.maxOrNull()?: -1.0
            if(last>2.2) frame.soak=min(1.0,(last-2.2)/3.8)
            if(last>6.4) { dropAt=doubleArrayOf(-100.0,-100.0,-100.0); nextDrop=0; frame.soak=0.0 }
            val after=frame.drops.count { it>0.96 }
            if(after>before) {
                buzz(1)
                if(after==1 && frame.done<0.5) { animate("done",frame.done,1.0,0.35); buzz(2) }
            }
            if(auto && !holding && now-lastTouch>2.6 && frame.lift<0.001 && nextDrop==0 && frame.done>0.999) { autoStart=now; manual=false }
        }
        ticks[0]=frame.done>0.5
    }
}
