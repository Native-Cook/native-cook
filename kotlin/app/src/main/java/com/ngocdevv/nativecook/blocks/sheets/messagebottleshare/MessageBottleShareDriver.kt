package com.ngocdevv.nativecook.blocks.sheets.messagebottleshare

import com.ngocdevv.nativecook.blocks.sheets.messagebottleshare.MessageBottleShareMotion as M
import kotlin.math.*

internal class MessageBottleShareDriver(auto: Boolean, private val reduced: Boolean) {
    var t = 0.0
    var now = 0.0
    var mode = if (auto && !reduced) 0 else 1
    var phase = 0
    var frame = M.Frame(M.Bottle())
    var sentChanged = -100.0
    private var lastTouch = -100.0
    private var phaseAt = 0.0
    private var from = M.Bottle()
    private var held = false
    private var dx = 0.0; private var dy = 0.0
    private var returnAt = -1.0; private var returnX = 0.0; private var returnY = 0.0
    private var fadeAt = -1.0; private var fadeFrom = 1.0; private var fadeTo = 1.0
    private var fingerFrom = 0.0; private var fingerAt = -100.0
    var cue: ((Int) -> Unit)? = null
    val animating get() = !reduced || fadeAt >= 0 || now-sentChanged < 0.44 || returnAt >= 0
    private fun go(p: Int) { phase = p; phaseAt = now }
    private fun setSent(value: Boolean) {
        if (frame.sent != value) { frame = frame.copy(sent = value); sentChanged = now }
    }
    private fun takeOver(l: M.Layout) {
        lastTouch = now
        if (mode == 0) {
            mode = 1; fingerFrom = frame.finger; fingerAt = now
            frame = frame.copy(fingerPress = 0.0)
            val b = frame.bottle
            if (frame.sent || (b.scale < l.scale*0.5 && b.alpha < 0.99)) {
                go(4); setSent(true); frame = frame.copy(bottle = b.copy(alpha = 0.0))
            } else if (b.floating > 0.5) go(0) else go(4)
        }
    }
    private fun throwBottle() {
        from = frame.bottle; cue?.invoke(1)
        if (reduced) {
            fadeAt = now; fadeFrom = from.alpha; fadeTo = 0.0; go(4); setSent(true)
        } else go(2)
    }
    fun press(l: M.Layout) {
        takeOver(l)
        if (phase == 0) throwBottle()
        else if (phase == 4) {
            setSent(false); frame = frame.copy(splash = -1.0)
            if (reduced) {
                frame = frame.copy(bottle = M.Bottle(l.homeX, l.homeY, M.REST_ANGLE, l.scale, 1.0, 0.0))
                fadeAt = now; fadeFrom = 0.0; fadeTo = 1.0; go(0)
            } else go(5)
        }
    }
    fun activate(l: M.Layout) { takeOver(l); if (phase == 0) throwBottle() }
    fun begin(l: M.Layout) {
        takeOver(l)
        if (phase != 0 && phase != 1) return
        if (phase == 0) { dx = 0.0; dy = 0.0 }
        held = true; returnAt = -1.0; go(1); cue?.invoke(0)
    }
    fun drag(x: Double, y: Double) {
        lastTouch = now
        if (held) { dx = x; dy = min(y, 30.0) }
    }
    fun end(y: Double, vy: Double) {
        lastTouch = now
        if (!held) return
        held = false
        if (vy < -500 || y < -70) throwBottle()
        else { returnAt = now; returnX = dx; returnY = dy }
    }
    fun step(dt: Double, l: M.Layout, auto: Boolean): M.Frame {
        now += dt
        val previous = t
        if (!reduced) t = (t+dt)%M.LOOP
        if (mode == 0) {
            val f = M.frameAt(t, l)
            setSent(f.sent); frame = f
            return frame
        }
        val tau = now-phaseAt
        val (bobY, bobA) = M.bob(t)
        var b = frame.bottle
        if (returnAt >= 0) {
            val e = M.cssEase(M.seg(now-returnAt, 0.0, 0.35))
            dx = returnX*(1-e); dy = returnY*(1-e)
            if (abs(dx) < 0.5 && abs(dy) < 0.5 && !held) { phase = 0; returnAt = -1.0 }
        }
        if (fadeAt >= 0) {
            val p = M.seg(now-fadeAt, 0.0, 0.2)
            val e = if (p < 0.5) 2*p*p else 1-(-2*p+2).pow(2)/2
            b = b.copy(alpha = fadeFrom+(fadeTo-fadeFrom)*e)
            if (p >= 1) fadeAt = -1.0
        }
        var splash = frame.splash
        when (phase) {
            0 -> b = M.Bottle(l.homeX, l.homeY+bobY, M.REST_ANGLE+bobA, l.scale, 1.0, b.alpha)
            1 -> b = M.Bottle(l.homeX+dx, l.homeY+bobY+dy, M.REST_ANGLE+bobA+dx*0.002,
                             l.scale, max(0.0, 1+dy/40), 1.0)
            2 -> {
                b = M.flightAt(l, from.x, from.y, from.angle, min(1.0, tau/M.FLIGHT))
                if (tau >= M.FLIGHT) { go(3); cue?.invoke(2); setSent(true) }
            }
            3 -> { b = M.driftAt(l, tau); splash = tau; if (tau >= M.DRIFT+0.6) go(4) }
            4 -> { splash = -1.0; if (!reduced) b = b.copy(alpha = 0.0) }
            5 -> { b = M.riseAt(l, t, min(1.0, tau/1.4)); if (tau >= 1.4) go(0) }
        }
        frame = frame.copy(bottle = b, splash = splash,
            finger = fingerFrom*(1-M.easeInOut(M.seg(now-fingerAt, 0.0, 0.15))))
        if (auto && phase == 0 && now-lastTouch > 2 && t < previous) mode = 0
        return frame
    }
}
