package com.ngocdevv.nativecook.blocks.navigation.greatwavemorning

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.LOOP
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.RESUME
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.MAX_SCROLL
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.loopScroll
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.frameAt
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.smooth

/** The finger owns the driver until scrolling, tab and row transitions have eased home. */
internal class GreatWaveMorningDriver(var t: Double = 0.0) {
    var mode = 0
    var last = -10.0
    var amb = t
    var s = 0.0
    var v = 0.0
    var dragging = false
    var raw = 0.0
    var releaseAt = -10.0
    var foamPow = 0.0
    val done = BooleanArray(5)
    val doneAt = DoubleArray(5) {
        -10.0
    }
    var tab = 0
    var tabPos = 0.0
    var tabV = 0.0
    var pressRow = -1
    var pressAt = -10.0
    var releaseRowAt = -10.0
    var pressLast = 0

    fun takeOver(now: Double) {
        last = now
        if (mode == 2) return
        mode = 2
        s = loopScroll(t)
        v = 0.0
        val f = frameAt(t)
        if (f.foam >= 0) {
            releaseAt = now - f.foam
            foamPow = 1.0
        }
    }

    fun step(dt: Double, now: Double, auto: Boolean) {
        amb = (amb + dt) % LOOP
        if (mode == 0) {
            if (auto) t = (t + dt) % LOOP
            return
        }
        val idle = now - last >= RESUME && !dragging && pressRow < 0
        if (auto && idle) {
            for (i in 0..4) if (done[i]) {
                done[i] = false
                doneAt[i] = now
            }
            tab = 0
        }
        if (!dragging) {
            if (s < 0 || s > MAX_SCROLL || (auto && idle)) {
                val target = if (auto && idle || s < 0) 0.0 else MAX_SCROLL
                val k = 170.0
                val c = 2 * 0.86 * sqrt(k)
                v += (-k * (s - target) - c * v) * dt
                s += v * dt
            } else {
                s += v * dt
                v *= exp(-dt * 3.2)
                if (abs(v) < 4) v = 0.0
            }
        }
        val k = 260.0
        val c = 2 * 0.8 * sqrt(k)
        tabV += (-k * (tabPos - tab) - c * tabV) * dt
        tabPos += tabV * dt
        if (auto && idle && abs(s) < 0.5 && abs(v) < 5 && abs(tabPos) < 0.01 && now - doneAt.max() > 0.4) {
            mode = 0
            t = 0.0
        }
    }

    fun frame(now: Double): GreatWaveMorningFrame {
        if (mode == 0) return frameAt(t)
        val values = List(5) {
            i ->
            val k = smooth((now - doneAt[i]) / 0.35)
            if (done[i]) k else 1 - k
        }
        val press = if (pressRow >= 0) smooth((now - pressAt) / 0.1) else 1 - smooth((now - releaseRowAt) / 0.18)
        return GreatWaveMorningFrame(amb, s, if (now - releaseAt < 1.6) now - releaseAt else -1.0, foamPow, values, tabPos, if (pressRow >= 0) pressRow else pressLast, press)
    }
}
