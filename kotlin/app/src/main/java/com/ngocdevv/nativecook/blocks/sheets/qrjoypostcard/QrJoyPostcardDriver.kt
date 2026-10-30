package com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.LOOP
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.RESUME
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.PEAK
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.T
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.inRestA
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.inPostcard
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.clamp01
import com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard.QrJoyPostcardMotion.wobble

/** Mutable touch state, owned by the UI thread. Ambient motion shares the same nine-second wrap. */
internal class QrJoyPostcardDriver(var t: Double = 0.0) {
    var mode = 0
    var stop = 0.0
    var rate = 1.0
    var last = -10.0
    var amb = t
    var ctaAim = 0.0
    var ctaP = 0.0
    var tiltX = 0.0
    var tiltY = 0.0
    var aimX = 0.0
    var aimY = 0.0
    var lean = 0.0
    var leanV = 0.0
    var bearAt = -1.0
    var jiggleAt = -1.0

    private fun follow(cur: Double, aim: Double, k: Double, dt: Double) = cur+(aim-cur)*(1-exp(-dt*k))

    fun step(dt: Double, now: Double, auto: Boolean): Double {
        if (mode == 1) {
            t+=dt*rate
            if (t >= stop) {
                t = if (stop >= LOOP) 0.0 else stop
                mode = 2
                rate = 1.0
                last = now
            }
        } else if (mode == 2) {
            if (auto && now-last >= RESUME) mode = 0
        } else if (auto) t = (t+dt)%LOOP
        amb = (amb+dt)%LOOP
        tiltX = follow(tiltX, aimX, 11.0, dt)
        tiltY = follow(tiltY, aimY, 11.0, dt)
        val k = 120.0
        val c = 2*0.35*sqrt(k)
        leanV+=(-k*(lean-tiltX)-c*leanV)*dt
        lean+=leanV*dt
        ctaP = follow(ctaP, ctaAim, if (ctaAim>0) 40.0 else 18.0, dt)
        return t
    }

    fun touched(now: Double) {
        last = now
        if (mode != 0) return
        rate = 1.0
        if (t >= T.press[0] && t<PEAK) {
            mode = 1
            stop = PEAK
        }
        else if (t >= T.back && t<T.lower[1]) {
            mode = 1
            stop = LOOP
        }
        else mode = 2
    }

    fun pressA(): Boolean {
        if (!inRestA(t)) return false
        t = T.press[0]
        mode = 1
        rate = 1.0
        stop = T.commit-0.001
        return true
    }

    fun releaseA(onTarget: Boolean) {
        if (onTarget) {
            mode = 1
            rate = 1.0
            stop = PEAK
        }
        else {
            t = 0.0
            mode = 2
        }
    }

    fun closeB(): Boolean {
        if (!inPostcard(t) && !(t >= PEAK && t<T.back)) return false
        t = T.back
        mode = 1
        rate = 1.5
        stop = LOOP
        return true
    }
    data class Live(
        val amb: Double = 0.0,
        val tiltX: Double = 0.0,
        val tiltY: Double = 0.0,
        val lean: Double = 0.0,
        val cta: Double = 0.0,
        val hop: Double = 0.0,
        val squash: Double = 0.0,
        val wave: Double = 0.0,
        val jiggle: Double = 0.0
    )

    fun live(now: Double): Live {
        var hop = 0.0
        var squash = 0.0
        var wave = 0.0
        if (bearAt >= 0) {
            val p = (now-bearAt)/1.25
            if (p >= 1) bearAt = -1.0 else {
                val hp = clamp01(p/0.36)
                hop = if (hp<1) 11*sin(PI*hp) else 0.0
                squash = if (hp<1) -0.045*sin(PI*hp) else 0.07*wobble((p-0.36)*1.25, 0.22, 0.14)
                wave = 4*sin(2*PI*1.5*p)*minOf(1.0, p*6, (1-p)*4)
            }
        }
        var jiggle = 0.0
        if (jiggleAt >= 0) {
            val p = now-jiggleAt
            if (p>0.9) jiggleAt = -1.0 else jiggle = wobble(p, 0.16, 0.2)
        }
        return Live(amb, tiltX, tiltY, lean, ctaP, hop, squash, wave, jiggle)
    }
}
