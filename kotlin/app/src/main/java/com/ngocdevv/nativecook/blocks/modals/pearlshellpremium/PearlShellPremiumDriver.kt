package com.ngocdevv.nativecook.blocks.modals.pearlshellpremium

import kotlin.math.*

internal data class PearlShellPremiumTween(val from: Double, val to: Double, val at: Double, val duration: Double, val curve: Int = 0) {
    fun value(now: Double): Double {
        val k = ((now-at)/duration).coerceIn(0.0, 1.0)
        val e = when (curve) {
            1 -> PearlShellPremiumMotion.bezier(k, 0.23, 1.0, 0.32, 1.0)
            2 -> PearlShellPremiumMotion.bezier(k, 0.77, 0.0, 0.175, 1.0)
            else -> if (k < 0.5) 2*k*k else 1-(-2*k+2).pow(2)/2
        }
        return from+(to-from)*e
    }
    fun done(now: Double) = now-at >= duration
}

internal class PearlShellPremiumDriver(val autoPlay: Boolean, val reduced: Boolean, val buzz: (Int)->Unit) {
    private val m = PearlShellPremiumMotion
    var frame = PearlShellPremiumMotion.Frame()
    var now = 0.0
    private var autoStart = 0.0
    private var automatic = autoPlay && !reduced
    private var lastTouch = -100.0
    private var resumeAsked = false
    private var dragging = false
    private var panStarted = false
    private var openFrom = m.CLOSED
    private var spring: PearlShellPremiumSpring? = null
    private var angle: PearlShellPremiumTween? = null
    private var glow: PearlShellPremiumTween? = null
    private var lift: PearlShellPremiumTween? = null
    private var yaw: PearlShellPremiumTween? = null
    private var finger: PearlShellPremiumTween? = null
    private var pulseAt: Double? = null
    private var closingHaptic = false
    val moving get() = !reduced || angle != null || glow != null || lift != null || yaw != null || finger != null
    fun step(dt: Double) {
        now += dt
        if (automatic) { frame = m.frameAt((now-autoStart) % m.LOOP); return }
        spring?.let {
            it.step(dt); frame.open = it.value
            if (it.finished) {
                spring = null
                if (closingHaptic) { closingHaptic = false; buzz(0) }
            }
        }
        angle?.let { frame.open = it.value(now); if (it.done(now)) angle = null }
        glow?.let { frame.glow = it.value(now); if (it.done(now)) glow = null }
        lift?.let { frame.lift = it.value(now); if (it.done(now)) lift = null }
        yaw?.let { frame.yaw = it.value(now); if (it.done(now)) yaw = null }
        finger?.let { frame.finger = it.value(now); if (it.done(now)) finger = null }
        pulseAt?.let {
            if (now-it >= 0.12) {
                glow = PearlShellPremiumTween(1.4, 1.0, it+0.12, 0.6, 1)
                frame.glow = glow!!.value(now); pulseAt = null
            }
        }
        if (!reduced) {
            frame.spin = (frame.spin+m.TAU/m.LOOP*dt) % m.TAU
            frame.phase = (frame.phase+m.TAU*2/m.LOOP*dt) % m.TAU
        }
        if (autoPlay && !reduced && !dragging && now-lastTouch >= 2) {
            if (!resumeAsked) {
                resumeAsked = true
                if (frame.open > m.CLOSED+0.01 || frame.glow > 0.001) setOpen(false, 0.0)
                yaw = PearlShellPremiumTween(frame.yaw, 0.0, now, 0.6)
            } else if (abs(frame.open-m.CLOSED) < 0.004 && frame.glow < 0.002 && frame.lift < 0.002 && abs(frame.yaw) < 0.002) {
                frame.spin = 0.0; frame.phase = 0.0; autoStart = now; automatic = true; resumeAsked = false
            }
        }
    }
    private fun takeover() {
        lastTouch = now; resumeAsked = false
        if (automatic) {
            automatic = false
            finger = PearlShellPremiumTween(frame.finger, 0.0, now, 0.15)
            frame.fingerPress = 0.0
        }
    }
    private fun setOpen(on: Boolean, velocity: Double) {
        pulseAt = null
        if (reduced) {
            spring = null; closingHaptic = false
            angle = PearlShellPremiumTween(frame.open, if (on) m.OPEN else m.CLOSED, now, 0.25)
            glow = PearlShellPremiumTween(frame.glow, if (on) 1.0 else 0.0, now, 0.25)
            lift = PearlShellPremiumTween(frame.lift, if (on) m.LIFT else 0.0, now, 0.25)
            return
        }
        val previous = spring?.velocity ?: 0.0
        angle = null
        spring = PearlShellPremiumSpring(frame.open, if (on) m.OPEN else m.CLOSED, if (on) 0.7 else 0.65, if (on) 0.55 else 0.62, previous+velocity, velocity)
        closingHaptic = !on
        glow = PearlShellPremiumTween(frame.glow, if (on) 1.0 else 0.0, now, if (on) 0.8 else 0.5, 1)
        lift = PearlShellPremiumTween(frame.lift, if (on) m.LIFT else 0.0, now, if (on) 1.0 else 0.45, if (on) 2 else 1)
        if (on) buzz(1)
    }
    fun begin() { takeover(); panStarted = false }
    fun drag(dx: Double, dy: Double, scale: Double) {
        lastTouch = now
        if (!panStarted && hypot(dx, dy) >= 2) {
            panStarted = true; dragging = true; openFrom = frame.open
            spring = null; angle = null; closingHaptic = false
        }
        if (!panStarted) return
        val raw = openFrom-dy/scale*0.95
        val lo = m.CLOSED
        val hi = m.OPEN+0.1
        frame.open = when {
            raw < lo -> lo-(lo-raw)/(1+(lo-raw)/0.08)
            raw > hi -> hi+(raw-hi)/(1+(raw-hi)/0.15)
            else -> raw
        }
        if (!reduced) { glow = null; pulseAt = null; frame.glow = m.clamp((frame.open-0.4)/0.6)*0.6 }
    }
    fun end(velocity: Double, scale: Double, cancelled: Boolean = false) {
        if (!cancelled) {
            if (panStarted) {
                val v = -velocity/scale*0.95
                setOpen(v > 1.5 || (v > -1.5 && frame.open > 0.55), v)
            } else if (frame.open > 0.8 && !reduced) {
                glow = PearlShellPremiumTween(frame.glow, 1.4, now, 0.12, 1)
                pulseAt = now; buzz(0)
            } else setOpen(true, 0.0)
        }
        dragging = false; lastTouch = now
    }
    fun toggle() { takeover(); setOpen(frame.open < 0.55, 0.0) }
}
