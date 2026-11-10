package com.ngocdevv.nativecook.blocks.modals.champagnersvp

import com.ngocdevv.nativecook.blocks.modals.champagnersvp.ChampagneRsvpMotion as M
import kotlin.math.*

internal class ChampagneRsvpDriver(val auto: Boolean, val reduced: Boolean, private val buzz: (Boolean) -> Unit) {
    var now = 0.0
    var done = false
    var frame = M.Frame()
    var manual = !auto || reduced
    var dragging = false
    private var autoStart = 0.0
    private var lastTouch = -100.0
    private var clinkedAt = -1.0
    private var clinkAt = -1.0
    private var burstAt = -1.0
    private var acceptAt = -1.0
    private var acceptFrom = -M.REST_LEAN
    private var leanFrom = -M.REST_LEAN
    private var resumeAt = -1.0
    private var resumeFrom = -M.REST_LEAN
    private var fingerAt = -1.0
    private var fingerFrom = 0.0
    val moving get() = !reduced || clinkedAt >= 0 || clinkAt >= 0 || burstAt >= 0 || acceptAt >= 0
    fun step(dt: Double) {
        now += dt
        if (!manual && auto && !reduced) {
            frame = M.at((now - autoStart) % M.LOOP)
            done = frame.done
            return
        }
        if (!reduced) frame.phase += 0.6 * dt
        if (fingerAt >= 0) {
            val p = M.seg(now, fingerAt, fingerAt + 0.15)
            val q = if (p < 0.5) 2 * p * p else 1 - (-2 * p + 2).pow(2) / 2
            frame.finger = fingerFrom * (1 - q)
            if (now >= fingerAt + 0.15) fingerAt = -1.0
        }
        if (acceptAt >= 0) {
            val k = M.seg(now, acceptAt, acceptAt + 0.48)
            frame.lean = acceptFrom + (M.MEET - acceptFrom) * k * k
            if (k >= 1) { acceptAt = -1.0; toast() }
        }
        if (clinkAt >= 0) {
            frame.clink = 0.001 + 0.999 * M.seg(now, clinkAt, clinkAt + 0.9)
            if (now >= clinkAt + 0.9) { frame.clink = 0.0; clinkAt = -1.0 }
        }
        if (burstAt >= 0) {
            frame.burst = 0.001 + 0.999 * M.seg(now, burstAt, burstAt + 1.4)
            if (now >= burstAt + 1.4) { frame.burst = 0.0; burstAt = -1.0 }
        }
        if (clinkedAt >= 0 && !dragging) {
            frame.lean = M.clinkLeanAt(now - clinkedAt)
            if (now - clinkedAt > 1.2) clinkedAt = -1.0
        }
        if (auto && !reduced && !dragging && clinkedAt < 0 && now - lastTouch >= 2) {
            if (resumeAt < 0) { resumeAt = now; resumeFrom = frame.lean; done = false }
            val k = M.seg(now, resumeAt, resumeAt + 0.4)
            val q = if (k < 0.5) 2 * k * k else 1 - (-2 * k + 2).pow(2) / 2
            frame.lean = resumeFrom + (-M.REST_LEAN - resumeFrom) * q
            if (abs(frame.lean + M.REST_LEAN) < 0.001 && frame.clink == 0.0 && frame.burst == 0.0) {
                frame.phase = 0.0; autoStart = now; manual = false; resumeAt = -1.0
            }
        }
        frame.done = done
    }
    fun takeOver() {
        lastTouch = now; resumeAt = -1.0
        if (!manual) {
            manual = true; fingerAt = now; fingerFrom = frame.finger; frame.fingerPress = 0.0
            if (frame.lean > -M.REST_LEAN + 0.002) clinkedAt = now - 0.3
        }
    }
    private fun toast() {
        clinkedAt = now; clinkAt = now; frame.clink = 0.001
        if (!reduced) { burstAt = now; frame.burst = 0.001 }
        buzz(false); done = true
    }
    fun accept() {
        if (done) { buzz(true); return }
        takeOver()
        if (reduced) toast() else { acceptAt = now; acceptFrom = frame.lean }
    }
    fun beginDrag() {
        if (reduced) return
        dragging = true; clinkedAt = -1.0; acceptAt = -1.0; leanFrom = frame.lean
    }
    fun drag(x: Float, dx: Float, l: ChampagneRsvpLayout) {
        lastTouch = now
        if (!dragging) return
        val inward = if (x < l.w / 2) dx else -dx
        frame.lean = (leanFrom + inward / (l.heroS * 2.6)).coerceIn(-0.3, M.MEET)
        if (frame.lean >= M.MEET - 0.0005 && frame.clink == 0.0) toast()
    }
    fun endDrag() {
        lastTouch = now
        if (dragging) { dragging = false; clinkedAt = now - 0.12 }
    }
}
