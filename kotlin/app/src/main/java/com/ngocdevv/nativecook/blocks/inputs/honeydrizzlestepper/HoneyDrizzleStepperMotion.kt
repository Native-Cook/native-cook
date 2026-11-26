package com.ngocdevv.nativecook.blocks.inputs.honeydrizzlestepper

import kotlin.math.*

/** Seconds, including the same analytic loop and live takeover clocks as hds-motion.ts. */
internal class HoneyDrizzleStepperMotion {
    data class Frame(val amount: Double = 2.0, val flow: Double = 0.0, val pop: Double = 1.0, val number: Int = 2, val still: Boolean = false)
    var out = Frame()
    var mode = 0; var last = -1.0; var start = -1.0; var flowFrom = 0.0; var carry = 1000.0
    var amount = 2.0; var flow = 0.0; var popAge = 1000.0; var dragFrom = 2.0
    var e0 = 2.0; var e1 = 2.0; var age = -1.0; var idle = 0.0
    var dragging = false; var homing = false; var touches = 0; var shown = 2; var ticks = 0
    companion object {
        const val LOOP = 6.0
        fun amountAt(t: Double): Double {
            fun io(x: Double) = if(x < .5) 2*x*x else 1-(-2*x+2).pow(2)/2
            return when { t < .6 -> 2.0; t < 2.4 -> 2+3*io((t-.6)/1.8); t < 3.4 -> 5.0; t < 5 -> 5-3*io((t-3.4)/1.6); else -> 2.0 }
        }
        private val changes = listOf(1.0/6,.5,5.0/6).map { y -> .6+1.8*(if(y < .5) sqrt(y/2) else 1-sqrt((1-y)/2)) } +
            listOf(1.0/6,.5,5.0/6).map { y -> 3.4+1.6*(if(y < .5) sqrt(y/2) else 1-sqrt((1-y)/2)) }
        fun changedAgo(t: Double) = changes.filter { it <= t }.minOfOrNull { t-it } ?: 1000.0
        fun pop(age: Double) = if(age < .25) 1+.12*(1-age/.25).pow(3) else 1.0
        fun loopFrame(time: Double, offset: Double = 0.0, carried: Double = 1000.0): Frame {
            val t = time%LOOP; val a = amountAt(t)
            return Frame(a,(t+offset)%LOOP,pop(min(changedAgo(t),carried)),floor(a+.5).toInt())
        }
    }
    private fun rounded(a: Double) = floor(a+.5)
    fun takeOver() {
        if(mode != 0) return
        val since = if(start < 0) 0.0 else last-start; val t = since%LOOP
        mode = 1; amount = amountAt(t); flow = (t+flowFrom)%LOOP; popAge = min(changedAgo(t),carry+since); shown = rounded(amount).toInt(); age = -1.0
    }
    fun easeTo(to: Double) { e0 = amount; e1 = to; age = 0.0 }
    fun touchDown() { takeOver(); touches++; idle = 0.0; homing = false }
    fun touchUp() { touches = max(0,touches-1); idle = 0.0 }
    fun dragStart() { takeOver(); dragging = true; homing = false; age = -1.0; dragFrom = amount }
    fun dragTo(dy: Double) { if(dragging) amount = (dragFrom-dy/60).coerceIn(1.0,5.0) }
    fun dragEnd() { if(!dragging) return; dragging = false; idle = 0.0; easeTo(rounded(amount)) }
    fun stepBy(dir: Int) {
        takeOver(); idle = 0.0; homing = false
        val to = ((if(age >= 0) e1 else rounded(amount))+dir).coerceIn(1.0,5.0)
        if(to != amount) easeTo(to)
    }
    fun setStill(still: Boolean) {
        if(still && mode != 2) {
            amount = if(mode == 0) 2.0 else rounded(amount); mode = 2; age = -1.0; dragging = false
            shown = rounded(amount).toInt(); out = Frame(amount = amount,number = shown,still = true)
        } else if(!still && mode == 2) { mode = 0; start = -1.0; flowFrom = 0.0; carry = 1000.0; amount = 2.0; age = -1.0 }
    }
    fun step(now: Double, autoPlay: Boolean) {
        val dt = if(last < 0) 0.0 else (now-last).coerceIn(0.0,.05)
        if(mode == 0) {
            if(autoPlay) {
                last = now; if(start < 0) start = now
                val since = now-start; out = loopFrame(since,flowFrom,carry+since); return
            }
            // Pause from the frame on screen before advancing the live clock by dt.
            takeOver()
        }
        last = now
        if(mode != 2) flow = (flow+dt)%LOOP
        if(!dragging && age >= 0) {
            age = min(.3,age+dt); amount = e0+(e1-e0)*(1-(1-age/.3).pow(3))
            if(age >= .3) { amount = e1; age = -1.0 }
        }
        val moving = dragging || age >= 0
        popAge += dt; val n = rounded(amount).toInt()
        if(n != shown) { shown = n; popAge = 0.0; if(!homing) ticks++ }
        out = Frame(amount,if(mode == 2) 0.0 else flow,if(mode == 2) 1.0 else pop(popAge),n,mode == 2)
        if(touches > 0 || moving) { if(!homing) idle = 0.0; if(moving) return }
        if(touches > 0) return; idle += dt
        if(!autoPlay || mode == 2 || idle < 2) return
        if(amount != 2.0) { homing = true; easeTo(2.0); return }
        mode = 0; homing = false; start = now; flowFrom = flow%1.5; carry = popAge; out = loopFrame(0.0,flowFrom,carry)
    }
}
