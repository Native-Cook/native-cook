package com.ngocdevv.nativecook.blocks.cards.snowglobegift

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

internal class SnowGlobeGiftDriver(private val auto: Boolean, private val reduced: Boolean) {
    var frame = SnowGlobeGiftMotion.Frame()
    var revision by mutableStateOf(0)
    var opened by mutableStateOf(false)
    var added by mutableStateOf(false)
    private var now = 0.0
    private var autoStart = 0.0
    private var lastTouch = -100.0
    private var automatic = auto && !reduced
    private var resumeAsked = false
    private var dragging = false
    private var velocity = 0.0
    private var prevX = 0.0
    private var fingerAt = -10.0
    private var fingerFrom = 0.0
    private var addedUntil = -1.0
    private var lidAnimation: Tween? = null
    private var amountAnimation: Tween? = null
    data class Tween(val from: Double,val to: Double,val at: Double,val duration: Double,val curve: Int) {
        fun done(now: Double) = now-at >= duration
        fun value(now: Double): Double {
            val p = ((now-at)/duration).coerceIn(0.0,1.0)
            val e = when (curve) {
                1 -> SnowGlobeGiftMotion.bezier(p,0.23,1.0,0.32,1.0)
                2 -> SnowGlobeGiftMotion.bezier(p,0.77,0.0,0.175,1.0)
                else -> if (p < 0.5) 2*p*p else 1-(-2*p+2).pow(2)/2
            }
            return from+(to-from)*e
        }
    }
    fun tick(elapsed: Double) {
        now += elapsed
        if (automatic) frame = SnowGlobeGiftMotion.frameAt((now-autoStart)%SnowGlobeGiftMotion.LOOP)
        else {
            lidAnimation?.let { frame.lid = it.value(now); frame.glow = frame.lid; if (it.done(now)) lidAnimation = null }
            amountAnimation?.let { frame.amount = it.value(now); if (it.done(now)) amountAnimation = null }
            if (frame.finger > 0) {
                val p = ((now-fingerAt)/0.15).coerceIn(0.0,1.0)
                frame.finger = fingerFrom*(1-if (p < 0.5) 2*p*p else 1-(-2*p+2).pow(2)/2)
            }
            if (!reduced) {
                val dt = min(0.05,elapsed)
                if (!dragging) {
                    velocity += (-90*frame.wobble-2*0.22*9.5*velocity)*dt
                    frame.wobble += velocity*dt
                }
                frame.swirl *= exp(-dt/1.3)
                if (frame.swirl < 0.002) frame.swirl = 0.0
                frame.spin += dt*6*frame.swirl
            }
            if (auto && !reduced && !dragging && now-lastTouch >= 2) {
                if (!resumeAsked) { resumeAsked = true; if (frame.lid > 0.001) setOpen(false) }
                else if (frame.lid < 0.001 && frame.swirl == 0.0 && abs(frame.wobble) < 0.003 && abs(velocity) < 0.03) {
                    resumeAsked = false; frame.wobble = 0.0; autoStart = now; automatic = true
                }
            }
        }
        if (added && now >= addedUntil) added = false
        opened = frame.lid > 0.5
        revision++
    }
    fun takeOver() {
        lastTouch = now; resumeAsked = false
        if (automatic) {
            automatic = false; velocity = 0.0; fingerAt = now; fingerFrom = frame.finger; frame.fingerPress = 0.0
            if (frame.lid > 0.001 && frame.lid < 0.999) setOpen(frame.lid > 0.5)
        }
    }
    fun setOpen(on: Boolean) {
        val duration = if (reduced) 0.25 else if (on) 0.5 else 0.6
        lidAnimation = Tween(frame.lid,if (on) 1.0 else 0.0,now,duration,if (reduced) 0 else if (on) 1 else 2)
        amountAnimation = Tween(frame.amount,if (on) 1.0 else 0.0,now,if (reduced) duration else if (on) 0.45 else 0.25,if (reduced) 0 else 1)
        opened = on
    }
    fun dragStart(x: Double) { if (!reduced) { dragging = true; prevX = x } }
    fun drag(x: Double,radius: Float) {
        lastTouch = now
        if (!dragging) return
        val lean = (x/(radius*3)).coerceIn(-0.3,0.3)
        velocity = (lean-frame.wobble)*60; frame.wobble = lean
        frame.swirl = min(1.0,frame.swirl+abs(x-prevX)*0.004); prevX = x
    }
    fun end(speed: Double,radius: Float,wasDrag: Boolean,cancelled: Boolean): Boolean {
        lastTouch = now
        if (dragging) { dragging = false; velocity = (speed/(radius*3)).coerceIn(-3.0,3.0); return frame.swirl > 0.3 }
        if (!wasDrag && !cancelled && !reduced) {
            takeOver(); velocity += 1.4; frame.swirl = min(1.0,frame.swirl+0.35); return true
        }
        return false
    }
    fun accessibilityShake() { takeOver(); if (!reduced) frame.swirl = min(1.0,frame.swirl+0.6) }
    fun cta() {
        if (!opened) { takeOver(); setOpen(true) }
        else { added = true; addedUntil = now+1.8 }
    }
    fun needsFrames() = automatic || (auto && !reduced) || dragging || added || lidAnimation != null || amountAnimation != null || frame.finger > 0 || (!reduced && (frame.swirl > 0 || abs(frame.wobble) > 0.00001 || abs(velocity) > 0.0001))
}
