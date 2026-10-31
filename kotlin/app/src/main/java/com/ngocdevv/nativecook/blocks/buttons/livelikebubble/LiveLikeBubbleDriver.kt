package com.ngocdevv.nativecook.blocks.buttons.livelikebubble

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import kotlin.math.*

internal class LiveLikeBubbleDriver {
    private val m = LiveLikeBubbleMotion
    var manual by mutableStateOf(true)
    var idle by mutableStateOf(true)
    var likes by mutableIntStateOf(m.BASE)
    var holding = false
    var allowAuto = false
    var reduced = false
    var clock by mutableDoubleStateOf(0.0)
    var autoStart = 0.0
    private val starts = DoubleArray(m.SLOTS) { -10.0 }
    private val lanes = DoubleArray(m.SLOTS)
    private var next = 0
    private var holdX = 0.0
    private var lastTouch = -100.0
    private var lastLand = -10.0
    private var lastThrow = -10.0
    private var pressFrom = 0.0
    private var pressTo = 0.0
    private var pressAt = -10.0
    private var pressDuration = 0.15
    private var fingerFrom = 0.0
    private var fingerAt = -10.0
    private fun inOut(u: Double) = if (u < 0.5) 2*u*u else 1-(-2*u+2).pow(2)/2
    private fun press(t: Double): Double {
        val u = m.seg(t,pressAt,pressAt+pressDuration)
        val e = if (pressTo == 1.0) 1-(1-u)*(1-u) else inOut(u)
        return pressFrom+(pressTo-pressFrom)*e
    }
    fun configure(auto: Boolean, reduce: Boolean) {
        val nextAuto = auto && !reduce
        if (allowAuto && !nextAuto && !manual) takeOver()
        allowAuto = nextAuto; reduced = reduce
        manual = !allowAuto; autoStart = clock
        if (reduce) fingerFrom = 0.0
    }
    fun frame(): LiveLikeBubbleMotion.Frame {
        val t = clock
        if (!manual && allowAuto) return m.frameAt((t-autoStart)%m.LOOP)
        val sw = if (reduced) 0.0 else starts.sumOf { m.pulse(t-it-m.FLIGHT-m.SWALLOW*0.5) }
        val finger = fingerFrom*(1-inOut(m.seg(t,fingerAt,fingerAt+0.15)))
        return LiveLikeBubbleMotion.Frame(likes,sw.coerceIn(-1.0,1.0),press(t),finger)
    }
    fun hearts(): Array<FloatArray> {
        if (!manual && allowAuto) return m.heartsAt((clock-autoStart)%m.LOOP)
        return Array(m.SLOTS) { m.heartAt(clock,starts[it],lanes[it],it) }
    }
    fun takeOver() {
        lastTouch = clock; idle = false
        if (!manual && allowAuto) {
            val f = frame(); likes = f.count; fingerFrom = f.finger; fingerAt = clock
            pressFrom = 0.0; pressTo = 0.0; pressAt = clock
            starts.fill(-10.0); lastLand = clock
        }
        manual = true
    }
    private fun throwHeart(lane: Double) {
        val i = next % m.SLOTS; next++
        starts[i] = clock; lanes[i] = lane; lastThrow = clock
    }
    fun begin(lane: Double) {
        takeOver(); holdX = lane.coerceIn(-0.9,0.9)
        pressFrom = press(clock); pressTo = 1.0; pressAt = clock; pressDuration = 0.09
        throwHeart(holdX); holding = true
    }
    fun end() {
        holding = false; lastTouch = clock
        pressFrom = press(clock); pressTo = 0.0; pressAt = clock; pressDuration = 0.15
    }
    fun tick(dt: Double, onLand: () -> Unit) {
        clock += dt.coerceIn(0.0,0.05)
        if (!manual) return
        val t = clock
        if (holding && t-lastThrow > 0.15) throwHeart(holdX+sin(t*7)*0.25)
        for (start in starts) {
            val landed = start+m.FLIGHT+m.SWALLOW
            if (landed <= t && landed > t-dt.coerceIn(0.0,0.05)) {
                likes++; lastLand = t; onLand()
            }
        }
        if (t-lastLand > 1.5 && likes > m.BASE && t-lastTouch > 0.25) {
            likes--; lastTouch = t-1.75+0.25
        }
        if (holding || t-lastThrow < m.FLIGHT+m.SWALLOW+0.1) lastTouch = t
        if (!holding && likes == m.BASE && t-lastTouch >= 2) {
            if (allowAuto) { autoStart = t; manual = false }
            idle = true
        }
    }
}
