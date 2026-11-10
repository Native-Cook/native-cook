package com.ngocdevv.nativecook.blocks.cards.smokedclochereveal

import kotlin.math.*

/** Single main-thread owner of touch, animation and the optional autoplay script. */
internal class SmokedClocheRevealDriver(val autoPlay: Boolean, val reduced: Boolean, initialCount: Int = 0) {
    private val m = SmokedClocheRevealMotion
    var frame = m.at(0.0)
        private set
    var now = 0.0
        private set
    var count = initialCount
        private set
    var haptic = -1
    private var manual = !autoPlay || reduced
    private var autoStart = 0.0
    private var lastTouch = -100.0
    private var resumeAsked = false
    private var previousLoop = -1.0
    private var spawn = false
    private var lifting = false
    private var previousFx = -999.0
    private var previousFy = -999.0
    private var liftTween: Tween? = null
    private var cartTween: Tween? = null
    private var fingerTween: Tween? = null
    private var cartAdding = false

    private data class Tween(val from: Double, val to: Double, val at: Double, val duration: Double, val curve: Int = 0) {
        var impacted = false
        val contact: Double?
            get() = when (curve) {
                4 -> at + duration * SmokedClocheRevealMotion.LAND_AT
                5 -> at + duration - 0.22
                else -> null
            }
        fun value(now: Double): Double {
            val p = SmokedClocheRevealMotion.seg(now, at, at + duration)
            if (p <= 0) return from
            if (p >= 1) return to
            contact?.let { contact ->
                // Touch uses three timings; the scripted setDown uses a sine for its rock.
                val down = contact - at
                val rock = (duration - down) / 2
                val t = now - at
                if (t < down) {
                    val u = t / down
                    return from * (1 - if (curve == 4) SmokedClocheRevealMotion.bezier(u) else u * u)
                }
                val hop = from * SmokedClocheRevealMotion.LAND_HOP
                if (t < down + rock) return hop * (1 - (1 - (t - down) / rock).pow(2))
                return hop * (1 - ((t - down - rock) / rock).pow(2))
            }
            val q = when (curve) {
                1 -> SmokedClocheRevealMotion.inside(p)
                2 -> SmokedClocheRevealMotion.bezier(p)
                3 -> SmokedClocheRevealMotion.out(p)
                else -> if (p < 0.5) 2 * p * p else 1 - (-2 * p + 2).pow(2) / 2
            }
            return from + (to - from) * q
        }
        fun finished(now: Double) = now >= at + duration
    }

    fun step(delta: Double, l: SmokedClocheRevealLayout) {
        val dt = delta.coerceIn(0.0, 0.05)
        now += dt
        if (!manual) {
            val t = (now - autoStart) % m.LOOP
            frame = m.at(t)
            if (previousLoop < m.REFILL && t >= m.REFILL) frame.mode = 3
            previousLoop = t
            when (frame.fingerAt) {
                0 -> {
                    frame.fingerX = l.cx.toDouble()
                    frame.fingerY = l.cy - 220 * min(frame.lift, 0.55) * 1.6
                }
                1 -> {
                    frame.fingerX = l.cx + (frame.fingerU - 0.5) * 260
                    frame.fingerY = l.cy + sin(frame.fingerU * PI * 2) * 50
                }
                else -> {
                    frame.fingerX = l.ctaX.toDouble()
                    frame.fingerY = l.ctaY.toDouble()
                }
            }
            frame.fingerDown = frame.fingerAt == 1 && frame.finger > 0.5
            frame.fx = if (frame.fingerDown) frame.fingerX else -999.0
            frame.fy = if (frame.fingerDown) frame.fingerY else -999.0
        } else {
            liftTween?.let {
                frame.lift = it.value(now)
                if (!it.impacted && it.contact?.let { contact -> now >= contact } == true) {
                    it.impacted = true
                    haptic = 1
                }
                if (it.finished(now)) liftTween = null
            }
            fingerTween?.let { frame.finger = it.value(now); if (it.finished(now)) fingerTween = null }
            cartTween?.let { tween ->
                frame.cart = tween.value(now)
                if (tween.finished(now)) {
                    if (cartAdding) {
                        count++
                        cartAdding = false
                        cartTween = Tween(1.0, 0.0, now + 0.5, if (reduced) 0.001 else 0.6, 3)
                    } else cartTween = null
                }
            }
            if (frame.mode == 3) frame.mode = 2
            if (spawn) { frame.mode = 3; spawn = false }
        }
        frame.time = now % m.LOOP
        frame.dt = if (reduced) 0.0 else dt
        frame.dx = if (frame.fingerDown && previousFx > -900) frame.fx - previousFx else 0.0
        frame.dy = if (frame.fingerDown && previousFy > -900) frame.fy - previousFy else 0.0
        previousFx = frame.fx
        previousFy = frame.fy
        if (!manual || !autoPlay || reduced || lifting || frame.fingerDown || now - lastTouch < 2) return
        if (!resumeAsked) {
            resumeAsked = true
            if (frame.lift > 0.01) cover()
        } else if (frame.lift < 0.001 && frame.cart < 0.001 && now - lastTouch > 3.4) {
            resumeAsked = false
            autoStart = now
            previousLoop = -1.0
            manual = false
            liftTween = null
            cartTween = null
        }
    }

    fun takeOver() {
        lastTouch = now
        resumeAsked = false
        if (!manual) {
            manual = true
            fingerTween = Tween(frame.finger, 0.0, now, 0.15)
            frame.press = 0.0
            frame.fingerDown = false
            if (frame.lift > 0.02 && frame.lift < 0.98) liftTween = Tween(frame.lift, if (frame.lift > 0.5) 1.0 else 0.0, now, 0.3)
            if (frame.cart > 0.01) cartTween = Tween(frame.cart, 0.0, now, 0.4)
        }
    }

    fun uncover() {
        liftTween = Tween(frame.lift, 1.0, now, if (reduced) 0.25 else 0.42, 1)
        frame.mode = 1
        haptic = 2
    }

    fun cover() {
        liftTween = Tween(frame.lift, 0.0, now, if (reduced) 0.25 else 0.8, if (reduced) 2 else 4)
        spawn = true
        frame.mode = 2
        if (reduced) haptic = 1
    }

    fun addToCart() {
        takeOver()
        if (frame.cart > 0.01) return
        cartAdding = true
        cartTween = Tween(frame.cart, 1.0, now, if (reduced) 0.001 else 0.7, 2)
        haptic = 0
    }

    fun begin(x: Double, y: Double) {
        takeOver()
        frame.fx = x
        frame.fy = y
        previousFx = x
        previousFy = y
        if (frame.lift > 0.98) frame.fingerDown = true
    }

    fun startPan() { if (frame.lift < 0.02) lifting = true }

    fun drag(x: Double, y: Double, translationY: Double) {
        lastTouch = now
        frame.fx = x
        frame.fy = y
        if (!lifting) return
        val raw = max(0.0, -translationY / 260)
        val lift = if (raw < m.SEAL) raw * 0.35 else m.SEAL * 0.35 + (raw - m.SEAL) * 1.1
        frame.lift = if (lift < 0.6) lift else 0.6 + 0.15 * tanh((lift - 0.6) / 0.15)
        liftTween = null
        if (frame.lift > 0.12 && frame.mode != 1) { frame.mode = 1; haptic = 1 }
    }

    fun end(pan: Boolean, tap: Boolean, velocityY: Double) {
        if (pan && lifting) {
            if (frame.lift > 0.35 || velocityY < -700) uncover()
            else liftTween = Tween(frame.lift, 0.0, now, max(0.12, 0.42 * sqrt(min(1.0, frame.lift / 0.35))) + 0.22, 5)
        } else if (!pan && tap) {
            takeOver()
            if (frame.lift > 0.98) cover()
        }
        finishTouch()
    }

    fun finishTouch() { lastTouch = now; lifting = false; frame.fingerDown = false }
    fun activate() { takeOver(); if (frame.lift > 0.5) cover() else uncover() }
    fun needsFrames() = !reduced || liftTween != null || cartTween != null || fingerTween != null

}
