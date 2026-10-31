package com.ngocdevv.nativecook.blocks.cards.marketbagcheckout

import com.ngocdevv.nativecook.blocks.cards.marketbagcheckout.MarketBagCheckoutMotion as Motion
import kotlin.math.*

/** Advances autoplay or touch-driven poses using seconds on a shared clock. */
internal class MarketBagCheckoutDriver(
    private val cue: (String) -> Unit,
    private val buzz: (Int) -> Unit,
) {
    var automatic = false
        private set

    var loopTime = 0.0
        private set

    var now = 0.0
        private set

    var holding = false
        private set

    private var touchAt = -10.0
    private var paidAt = -10.0
    private var resetAt = -10.0
    private val packAt = DoubleArray(5) { -1.0 }
    private val unpackAt = DoubleArray(5) { -1.0 }
    private var hold = 0.0
    private var ticks = 0
    var pose = Motion.Pose()
        private set

    /**
     * Advances by deltaSeconds; silent autoplay resumes only after the existing rest guards pass.
     */
    fun step(deltaSeconds: Double, autoPlay: Boolean): Motion.Pose {
        now += deltaSeconds
        val anyPacked = (0..4).any { packAt[it] >= 0 && unpackAt[it] < 0 }
        if (
            autoPlay &&
                !automatic &&
                !anyPacked &&
                hold < 0.001 &&
                now - touchAt > 2 &&
                paidAt < 0 &&
                now - resetAt > 1.2
        ) {
            automatic = true
            loopTime = 0.0
        }
        if (automatic) {
            loopTime += deltaSeconds
            if (loopTime >= Motion.LOOP) loopTime -= Motion.LOOP
            pose = Motion.script(loopTime)
            return pose
        }
        if (paidAt < 0) {
            if (holding) {
                hold = min(1.0, hold + deltaSeconds / Motion.HOLD)
                val step = floor(hold * 10).toInt()
                if (step != ticks) {
                    ticks = step
                    buzz(0)
                }
                if (hold >= 1) {
                    paidAt = now
                    holding = false
                    buzz(2)
                    cue("paid")
                    cue("cinch")
                }
            } else hold = max(0.0, hold - deltaSeconds * 2.4)
        }
        val nextPose = Motion.Pose(bag = now)
        for (itemIndex in 0..4) {
            val packedAt = packAt[itemIndex]
            val unpackedAt = unpackAt[itemIndex]
            if (packedAt < 0) continue
            if (unpackedAt < 0) {
                nextPose.fly[itemIndex] =
                    Motion.easeInOut(Motion.seg(now - packedAt, 0.0, Motion.FLY))
                if (now - packedAt - deltaSeconds < Motion.FLY && now - packedAt >= Motion.FLY) {
                    buzz(1)
                    cue("drop")
                }
                nextPose.land[itemIndex] =
                    if (now - packedAt >= Motion.FLY) packedAt + Motion.FLY else -1.0
            } else {
                val flight =
                    1 - Motion.easeInOut(Motion.seg(now - unpackedAt, 0.0, Motion.FLY * 0.8))
                nextPose.fly[itemIndex] = flight
                nextPose.land[itemIndex] = packedAt + Motion.FLY
                nextPose.leave[itemIndex] = unpackedAt
                if (flight <= 0 && now - unpackedAt > 1) {
                    packAt[itemIndex] = -1.0
                    unpackAt[itemIndex] = -1.0
                }
            }
        }
        val reset = if (resetAt > 0) Motion.seg(now - resetAt, 0.0, 0.35) else 0.0
        if (resetAt > 0 && now - resetAt > 0.35) {
            paidAt = -10.0
            hold = 0.0
        }
        nextPose.hold = if (paidAt >= 0) 1 - reset else hold
        nextPose.paid = if (paidAt >= 0) now - paidAt else -1.0
        nextPose.reset = reset
        pose = nextPose
        return nextPose
    }

    /** Transfers the script's pack, hold, paid and reset beats into the manual clock. */
    private fun interrupt() {
        touchAt = now
        if (!automatic) return
        automatic = false
        for (itemIndex in 0..4) {
            val scriptPackAt = 1 + itemIndex * 0.32
            val scriptUnpackAt = 8.55 + (4 - itemIndex) * 0.12
            if (loopTime >= 9.5 || loopTime < scriptPackAt) {
                packAt[itemIndex] = -1.0
                unpackAt[itemIndex] = -1.0
            } else {
                packAt[itemIndex] = now - (loopTime - scriptPackAt)
                unpackAt[itemIndex] =
                    if (loopTime >= scriptUnpackAt) now - (loopTime - scriptUnpackAt) else -1.0
            }
        }
        hold = pose.hold
        paidAt = if (loopTime >= 4.25 && loopTime < 8.55) now - (loopTime - 4.25) else -10.0
        resetAt = if (loopTime >= 8.55 && loopTime < 9.5) now - (loopTime - 8.55) else -10.0
    }

    /** Starts a flight for one row, preserving the current scripted pose on takeover. */
    fun toggle(itemIndex: Int) {
        interrupt()
        if (paidAt < 0) {
            if (packAt[itemIndex] < 0) {
                packAt[itemIndex] = now
                unpackAt[itemIndex] = -1.0
            } else if (unpackAt[itemIndex] < 0 && now - packAt[itemIndex] > Motion.FLY)
                unpackAt[itemIndex] = now
        }
        cue("lift")
    }

    /** Begins the 1.25-second hold, or unpacks a paid basket with 0.12-second spacing. */
    fun holdStart() {
        interrupt()
        if (paidAt >= 0) {
            resetAt = now
            for (itemIndex in 0..4) if (packAt[itemIndex] >= 0 && unpackAt[itemIndex] < 0)
                unpackAt[itemIndex] = now + (4 - itemIndex) * 0.12
            return
        }
        holding = true
        ticks = 0
        var pendingItemCount = 0
        for (itemIndex in 0..4) if (packAt[itemIndex] < 0) {
            packAt[itemIndex] = now + pendingItemCount * 0.18
            unpackAt[itemIndex] = -1.0
            pendingItemCount++
        }
    }

    /** Releases the hold and records the latest touch time in seconds. */
    fun holdEnd() {
        holding = false
        touchAt = now
    }

    /** Whether manual motion still needs frame updates. */
    fun isMoving() =
        holding ||
            hold > 0 && paidAt < 0 ||
            (0..4).any {
                packAt[it] >= 0 &&
                    (now - packAt[it] < 2 || unpackAt[it] >= 0 && now - unpackAt[it] < 1.5)
            } ||
            paidAt >= 0 && now - paidAt < 1.2
}
