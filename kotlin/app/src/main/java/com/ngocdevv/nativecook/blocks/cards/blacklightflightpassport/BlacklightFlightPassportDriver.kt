package com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport

import kotlin.math.exp
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportMotion.LOOP
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportMotion.RESUME
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportMotion.UV_PEAK
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportMotion.T
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportMotion.isUv

/** Mutable touch state, owned by the UI thread; caller clamps frame time to 50 ms. */
internal class BlacklightFlightPassportDriver(initial: Double = 0.0) {
    var t = initial
    var mode = 0
    var stop = 0.0
    var rate = 1.0
    var last = -10.0
    var amb = initial
    var tiltX = 0.0
    var tiltY = 0.0
    var torch = 0.0
    var torchAim = 0.0
    var tx = 200.0
    var ty = 400.0
    var pressAim = 0.0
    var pressP = 0.0
    private fun follow(current: Double, aim: Double, k: Double, dt: Double) = current + (aim - current) * (1 - exp(-dt * k))
    fun step(dt: Double, now: Double, auto: Boolean): Double {
        if (mode == 1) {
            t += dt * rate
            if (t >= stop) {
                t = if (stop >= LOOP) 0.0 else stop
                mode = 2
                rate = 1.0
                last = now
            }
        } else if (mode == 2) {
            if (auto && torchAim == 0.0 && now - last >= RESUME) mode = 0
        } else if (auto) t = (t + dt) % LOOP
        amb = (amb + dt) % LOOP
        torch = follow(torch, torchAim, if (torchAim > 0) 14.0 else 9.0, dt)
        pressP = follow(pressP, pressAim, if (pressAim > 0) 40.0 else 16.0, dt)
        return t
    }
    fun touched(now: Double) {
        last = now
        if (mode != 0) return
        rate = 1.0
        if (t >= T.press[0] && t < UV_PEAK) {
            mode = 1
            stop = UV_PEAK
        } else if (t >= T.pressOff[0] && t < T.uvOff[1]) {
            mode = 1
            stop = LOOP
        } else mode = 2
    }
    fun toggleUv(): Boolean {
        if (isUv(t) || (t >= UV_PEAK && t < T.uvOff[0])) {
            t = T.pressOff[0]
            stop = LOOP
            rate = 1.25
        } else {
            t = if (t >= T.draw[1] + 0.3 || t < T.fade[0]) T.press[0] else t
            stop = UV_PEAK
            rate = 1.25
        }
        mode = 1
        return isUv(stop - 0.01)
    }
}
