package com.ngocdevv.nativecook.blocks.gestures.parkassistscrub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

import com.ngocdevv.nativecook.blocks.gestures.parkassistscrub.ParkAssistScrubMotion as M

// Snapshot reads are confined to Canvas drawing and semantics, never the whole screen composition.
internal class ParkAssistScrubDriver(val autoPlay: Boolean, reduced: Boolean, val buzz: (String) -> Unit) {
    private var reduced = reduced
    var frame by mutableStateOf(ParkAssistScrubFrame())
        private set
    private var manual = !autoPlay || reduced
    private var clock = 0.0
    private var epoch = 0.0
    private var velocity = 0.0
    private var lastTouch = -100.0
    private var lastBeep = 0.0
    private var resumeAsked = false
    private var parkedAt: Double? = null
    private var exitAt: Double? = null
    private var hazardAt: Double? = null
    private var exitS = 0.0
    private var exitParked = 0.0
    private var exitAssist = 0.0
    private var fingerAt: Double? = null
    private var fingerFrom = 0.0
    private var buttonDown = false
    private var outDown = false
    private fun takeOver() {
        lastTouch = clock; resumeAsked = false
        if (!manual) {
            manual = true; fingerFrom = frame.finger; fingerAt = clock
            frame = frame.copy(holding=0.0,hazard=0.0); velocity = 0.0; parkedAt = null; hazardAt = null
            if (frame.out > 0.5) driveOut()
            else frame = frame.copy(out=0.0,parked=if (frame.s > 0.999) 1.0 else 0.0)
        }
    }
    fun begin(inButton: Boolean) {
        takeOver(); buttonDown = inButton; outDown = inButton && frame.parked > 0.5
        if (inButton && frame.parked <= 0.5 && frame.out != 1.0) {
            frame = frame.copy(holding=1.0); buzz("press")
        }
    }
    fun end(cancelled: Boolean = false, tapValid: Boolean = true) {
        lastTouch = clock; frame = frame.copy(holding=0.0)
        if (buttonDown && outDown && !cancelled && tapValid && frame.parked > 0.5) driveOut()
        buttonDown = false; outDown = false
    }
    fun accessibilityHold() { if (frame.holding == 1.0) end() else begin(true) }
    fun accessibilityOut() { takeOver(); driveOut() }
    private fun driveOut() {
        exitAt = clock; exitS = frame.s; exitParked = frame.parked; exitAssist = frame.assist
        frame = frame.copy(out=1.0); parkedAt = null
    }
    fun updateReduced(value: Boolean) {
        if (value == reduced) return
        if (value && !manual) takeOver()
        reduced = value
        if (autoPlay && !value) { epoch = clock; manual = false } else manual = true
    }
    fun tick(elapsed: Double) {
        val dt = elapsed.coerceIn(0.0,0.05); clock += dt
        if (!manual) { frame = M.frameAt(((clock-epoch)%M.LOOP+M.LOOP)%M.LOOP); return }
        var f = frame
        val exit = exitAt; val park = parkedAt
        if (exit != null) {
            val u = M.seg(clock,exit,exit+if (reduced) 0.6 else 1.8)
            val fade = 1-M.timing(M.seg(clock,exit,exit+0.3))
            f = f.copy(s=exitS*(1-M.driveEase(u)),parked=exitParked*fade,assist=exitAssist*fade)
            if (u >= 1) { exitAt = null; f = f.copy(out=0.0,s=0.0) }
        } else if (park != null) {
            f = f.copy(parked=M.timing(M.seg(clock,park,park+0.3)))
        } else if (f.parked < 0.5) {
            val want = if (f.holding == 1.0) M.SPEED else 0.0
            velocity += (want-velocity)*min(1.0,dt*if (want > 0) 5 else 9)
            val next = f.s+velocity*dt*(1-0.65*M.smooth(0.8,1.0,f.s))
            if (next >= 0.999) { f = f.copy(s=1.0); velocity = 0.0; parkedAt = clock; hazardAt = clock; buzz("parked") }
            else f = f.copy(s=max(0.0,next))
            if (f.holding == 1.0) f = f.copy(assist=min(1.0,f.assist+dt*4))
            val gap = M.gapAt(f.s)
            if (f.holding == 1.0 && velocity > 0.02 && gap < 1.4 && clock-lastBeep > 0.1+gap*0.45) {
                lastBeep = clock; buzz("beep")
            }
        }
        hazardAt?.let {
            val u = M.seg(clock,it,it+0.7)
            val hz = if (u >= 1) 0.0 else if (reduced) 1.0
                else max(0.0,sin(PI*min(1.0,u*2)))+max(0.0,sin(PI*max(0.0,u*2-1)))
            f = f.copy(hazard=hz)
        }
        fingerAt?.let { f = f.copy(finger=fingerFrom*(1-M.timing(M.seg(clock,it,it+0.15)))) }
        val moving = abs(velocity) > 0.01 || f.out == 1.0
        val wantBrake = if (!moving && f.s > 0.02) 1.0 else 0.0
        f = f.copy(brake=f.brake+(wantBrake-f.brake)*min(1.0,dt*12))
        if (f.holding == 1.0 || moving) lastTouch = clock
        frame = f
        if (autoPlay && !reduced && clock-lastTouch >= 2) {
            if (!resumeAsked) { resumeAsked = true; if (f.s > 0.001) driveOut() }
            else if (f.out == 0.0 && f.s < 0.001) {
                resumeAsked = false; velocity = 0.0; epoch = clock; manual = false
            }
        }
    }
}
