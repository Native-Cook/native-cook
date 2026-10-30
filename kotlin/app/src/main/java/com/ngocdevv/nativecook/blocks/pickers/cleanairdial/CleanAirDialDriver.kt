package com.ngocdevv.nativecook.blocks.pickers.cleanairdial

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialMotion.Frame
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialMotion.frameAt
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialMotion.T
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialMotion.LOOP
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialMotion.RESUME
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialMotion.pmOf
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialMotion.levelAt
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialMotion.omegaAt
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialMotion.TAU
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialMotion.smooth
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialTheme as Theme

/** Mutable touch driver owned by the UI thread, matching RN's integration order. */
internal class CleanAirDialDriver(var t: Double = 0.0) {
    var mode = 0
    var last = -10.0
    var amb = t
    var phi0 = 0.0
    var g = Theme.ticks[1]
    var gv = 0.0
    var level = 1
    var dragging = 0
    var dragG = 0.0
    var f = 0.0
    var ret = 0
    var phi = 0.0
    var power = true
    var powerAt = -10.0
    var timer = false
    var timerAt = -10.0
    var scenes = true
    var scenesAt = -10.0
    var auto = false
    var autoAt = -10.0
    var press = -1
    var pressAt = -10.0
    var releaseAt = -10.0
    var pressLast = 0
    var homing = 0

    fun takeOver(now: Double) {
        last = now
        homing = 0
        if (mode == 2) return
        val frame = frameAt(t, phi0)
        mode = 2
        g = frame.g
        gv = 0.0
        level = if (t >= T.up[0] && t < T.down[0]) 2 else 1
        f = frame.f
        ret = frame.ret
        phi = frame.phi
        power = true
        timer = false
        scenes = true
        auto = false
    }

    fun step(dt: Double, now: Double, autoplay: Boolean) {
        amb = (amb + dt) % LOOP
        if (mode == 0) {
            if (autoplay) t = (t + dt) % LOOP
            return
        }
        if (auto && power && dragging == 0) level = if (pmOf(f) > 7) 2 else 1
        if (autoplay && dragging == 0 && press < 0 && now - last >= RESUME && homing == 0) {
            homing = 1
            level = 1
            if (!power) {
                power = true
                powerAt = now
            }
            if (timer) {
                timer = false
                timerAt = now
            }
            if (!scenes) {
                scenes = true
                scenesAt = now
            }
            if (auto) {
                auto = false
                autoAt = now
            }
        }
        val tickTarget = if (dragging == 1) dragG else if (power) Theme.ticks[level] else 0.0
        if (dragging == 1) {
            g += (tickTarget - g) * min(1.0, dt * 22)
            gv = 0.0
        } else {
            val k = 140.0
            val c = 2 * 0.62 * sqrt(k)
            gv += (-k * (g - tickTarget) - c * gv) * dt
            g += gv * dt
        }
        val fTarget = if (power) Theme.target[levelAt(max(0.0, g))] else -0.5
        val om = if (power) omegaAt(max(0.0, g)) else 0.0
        if (fTarget > f) {
            f = min(fTarget, f + (fTarget - f + 0.05) * 0.24 * om * dt)
            ret = 0
        } else if (fTarget < f) {
            f = max(fTarget, f - (f - fTarget + 0.05) * 0.32 * dt)
            ret = 1
        }
        phi += om * dt
        if (homing == 1 && abs(g - Theme.ticks[1]) < 0.05 && abs(gv) < 0.2 && abs(f) < 0.004) {
            mode = 0
            t = 0.0
            phi0 = phi % TAU
            homing = 0
        }
    }

    fun frame(now: Double): Frame {
        if (mode == 0) return frameAt(t, phi0)
        val pressK = if (press >= 0) smooth((now - pressAt) / 0.1) else 1 - smooth((now - releaseAt) / 0.16)
        fun fade(on: Boolean, at: Double, dur: Double) = if (on) smooth((now - at) / dur) else 1 - smooth((now - at) / dur)
        return Frame(amb, g, phi, f, ret, fade(power, powerAt, 0.35), fade(timer, timerAt, 0.25), now - timerAt,
        fade(scenes, scenesAt, 0.25), fade(auto, autoAt, 0.25), if (dragging == 1) dragG else -1.0,
        if (press >= 0) press else pressLast, pressK, 1 - fade(power, powerAt, 0.35))
    }
}
