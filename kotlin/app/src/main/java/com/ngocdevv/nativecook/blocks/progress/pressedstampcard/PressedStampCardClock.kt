package com.ngocdevv.nativecook.blocks.progress.pressedstampcard

import kotlin.math.hypot
import kotlin.math.max

/** One owner for live stamps, long-press magnetism and the edge-on redemption reset. */
internal class PressedStampCardClock(private val automatic: Boolean, initialCount: Int = 5) {
    private var origin = 0.0
    private var base = initialCount
    private var manual = !automatic
    private var lastTouch = 0.0
    private var eventStarted = -1.0
    private var eventTarget = -1
    private var redeemStarted = -1.0
    private var queued = false
    private var didRedeem = false
    private var pressedAt = -1.0
    private var button = false
    private var startY = 0.0
    private var holdX = 0.0
    private var holdY = 0.0
    private var dropX: Double? = null
    private var dropY = 0.0
    private var magnet = false
    var held = false
        private set
    var pull = 0.0
        private set
    val animating get() = eventStarted >= 0 || redeemStarted >= 0
    val magnetIndex get() = if (held && magnet) base.coerceAtMost(7) else -1

    fun frame(now: Double, reduced: Boolean): PressedStampCardFrame {
        if (origin == 0.0) origin = now
        if (!manual) return if (reduced) PressedStampCardMotion.rest(5) else PressedStampCardMotion.frameAt(now - origin)
        if (pressedAt >= 0 && !button && base < 8 && eventStarted < 0 && now - pressedAt >= .18) held = true
        if (redeemStarted >= 0) {
            val age = now - redeemStarted
            val f = PressedStampCardMotion.flip(age, reduced)
            if (age >= .7 || reduced) { base = 0; redeemStarted = -1.0; lastTouch = now }
            return f
        }
        var f = PressedStampCardMotion.rest(base)
        f.phase = now - origin
        if (eventStarted >= 0) {
            val age = now - eventStarted
            f = PressedStampCardMotion.stamp(f, age, eventTarget, reduced)
            val impact = if (eventTarget == 7) 1.14 else .96
            val fromX = dropX
            if (fromX != null && age >= impact - .14 && age < impact && !reduced) {
                val p = PressedStampCardMotion.segment(age, impact - .14, impact).let { it * it * it }
                f.fly.x = PressedStampCardMotion.mix(fromX, PressedStampCardMotion.sealX(eventTarget), p)
                f.fly.y = PressedStampCardMotion.mix(dropY, PressedStampCardMotion.sealY(eventTarget), p)
            }
            if (age >= if (reduced) .4 else if (eventTarget == 7) 2.65 else 2.2) {
                base = eventTarget + 1; eventStarted = -1.0; dropX = null; lastTouch = now
                if (queued && base < 8) { queued = false; scan(now, reduced) }
            }
        }
        if (held && !reduced) {
            f.fly = PressedStampCardSeal(holdX, holdY, 52.0, 1.0, lift = 1.0)
        }
        if (didRedeem) f.orders = 5.0
        if (pressedAt < 0 && eventStarted < 0 && lastTouch > 0 && now - lastTouch > 2 && automatic && !didRedeem) {
            manual = false; origin = now
        }
        return f
    }
    private fun takeOver(now: Double, reduced: Boolean) {
        if (!manual) {
            val f = frame(now, reduced)
            base = f.count
            // A touch continues an in-flight source impact rather than stamping the same well twice.
            if (f.target >= 0 && f.event >= 0 && f.event < if (f.target == 7) 2.65 else 2.2) {
                base = f.target; eventTarget = f.target; eventStarted = now - f.event
            }
            manual = true
        }
        lastTouch = now
    }
    fun scan(now: Double, reduced: Boolean) {
        takeOver(now, reduced)
        if (held || redeemStarted >= 0) return
        if (eventStarted >= 0 && eventTarget == 7 && now - eventStarted >= 1.14) {
            base = 8; eventStarted = -1.0; dropX = null
        }
        if (eventStarted >= 0) { queued = true; return }
        if (base >= 8) { redeemStarted = now; didRedeem = true; return }
        didRedeem = false
        eventTarget = base; eventStarted = now; dropX = null
    }
    fun begin(x: Double, y: Double, now: Double, reduced: Boolean): Boolean {
        val onCard = x in 20.0..382.0 && y in 118.0..462.0
        val onButton = x in 20.0..382.0 && y in 478.0..534.0
        if (!onCard && !onButton) return false
        takeOver(now, reduced); pressedAt = now; startY = y; button = onButton; held = false; pull = 0.0
        move(x, y, now)
        return true
    }
    fun move(x: Double, y: Double, now: Double) {
        if (pressedAt < 0) return
        if (!button && base == 8) pull = max(0.0, y - startY).coerceAtMost(120.0)
        if (!button && base < 8 && eventStarted < 0 && now - pressedAt >= .18) held = true
        if (base < 8) {
            val cx = PressedStampCardMotion.sealX(base); val cy = PressedStampCardMotion.sealY(base)
            val px = x - 20; val py = y - 118
            magnet = hypot(px - cx, py - cy) <= 48
            holdX = PressedStampCardMotion.mix(px, cx, if (magnet) .35 else 0.0)
            holdY = PressedStampCardMotion.mix(py, cy, if (magnet) .35 else 0.0)
        }
    }
    fun end(x: Double, y: Double, now: Double, reduced: Boolean): Boolean {
        move(x, y, now)
        val wasHeld = held
        val didAct = when {
            button && x in 20.0..382.0 && y in 478.0..534.0 -> { held = false; scan(now, reduced); true }
            !button && base == 8 && eventStarted < 0 && y - startY >= 72 -> { redeemStarted = now; didRedeem = true; true }
            wasHeld && magnet && eventStarted < 0 && base < 8 -> {
                dropX = holdX; dropY = holdY; eventTarget = base; eventStarted = now - if (base == 7) 1.0 else .82; true
            }
            else -> false
        }
        held = false; magnet = false; pressedAt = -1.0; pull = 0.0; lastTouch = now
        return didAct
    }
    fun cancel(now: Double) { held = false; magnet = false; pressedAt = -1.0; pull = 0.0; lastTouch = now }
}
