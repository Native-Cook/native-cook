package com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite

import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteMotion.Cup
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteMotion.Fold
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteMotion.Invite
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteMotion.Reduced
import com.ngocdevv.nativecook.blocks.onboarding.findfriendsinvite.FindFriendsInviteMotion.Scan

internal enum class FindFriendsInviteHaptic {
    LIGHT,
    SUCCESS,
}

/**
 * Owns the clocks. In autoPlay they come from the loop time; touch takes over, each running clock plays
 * to its end, and the loop comes back [FindFriendsInviteMotion.RESUME_AFTER] seconds later. Reduce Motion
 * and the screen reader are followed live through [update]: the first turns presses into opacity dips,
 * either one stops the loop (running clocks play to rest).
 */
internal class FindFriendsInviteController(private val autoPlay: Boolean) {
    private enum class Mode {
        AUTO,
        MANUAL,
    }

    private var reduced = false
    private var looping = autoPlay
    private var mode = if (looping) Mode.AUTO else Mode.MANUAL
    private var inviteTime = 0.0
    private var scanTime = 0.0
    private var cupTime = Cup.end
    private var cupTapTime = Cup.end
    private var loopTime = 0.0
    private var inviteHeld = false
    private var scanHeld = false
    private var lastFrameNanos = -1L
    private var idleSinceNanos = -1L
    private var warmFramesLeft = FindFriendsInviteMotion.Warm.frames

    private val inviteEnd get() = if (reduced) Reduced.inviteEnd else FindFriendsInviteMotion.INVITE_END
    private val scanEnd get() = if (reduced) Reduced.scanEnd else Scan.end

    /** Called when Reduce Motion or the screen reader changes. */
    fun update(reduced: Boolean, calm: Boolean) {
        this.reduced = reduced
        looping = autoPlay && !calm
        idleSinceNanos = -1L
        lastFrameNanos = -1L
        if (!looping && mode == Mode.AUTO) mode = Mode.MANUAL
    }

    fun snapshot(): FindFriendsInviteMotion.Clocks {
        return FindFriendsInviteMotion.Clocks(
            invite = inviteTime,
            scan = scanTime,
            cup = cupTime,
            cupTap = cupTapTime,
            reduced = reduced,
            warm = warmFramesLeft > 0,
        )
    }

    fun shouldTick(): Boolean {
        if (warmFramesLeft > 0) return true
        if (mode == Mode.AUTO) return looping
        if (inviteHeld || scanHeld) return true
        if (inviteTime > 0.0 && inviteTime < inviteEnd) return true
        if (scanTime > 0.0 && scanTime < scanEnd) return true
        if (cupTime > 0.0 && cupTime < Cup.end) return true
        if (cupTapTime < Cup.end) return true
        return looping
    }

    /** Seconds the loop has nothing to draw for (its rest stretch), or 0 when something moves. */
    fun restSeconds(): Double {
        if (mode != Mode.AUTO || !looping || warmFramesLeft > 0) return 0.0
        val restFrom = FindFriendsInviteMotion.REST_FROM
        val restUntil = FindFriendsInviteMotion.REST_UNTIL
        return when {
            loopTime >= restFrom -> FindFriendsInviteMotion.LOOP - loopTime + restUntil
            loopTime < restUntil -> restUntil - loopTime
            else -> 0.0
        }
    }

    /** After waiting out the rest, continue where the loop is next due to move. */
    fun skipRest() {
        if (mode != Mode.AUTO) return
        loopTime = FindFriendsInviteMotion.REST_UNTIL
        lastFrameNanos = -1L
    }

    fun tick(nowNanos: Long): List<FindFriendsInviteHaptic> {
        val previousNanos = lastFrameNanos
        lastFrameNanos = nowNanos
        val delta = if (previousNanos < 0L) {
            0.0
        } else {
            ((nowNanos - previousNanos) / 1_000_000_000.0).coerceIn(0.0, 0.1)
        }
        if (warmFramesLeft > 0) warmFramesLeft--

        if (mode == Mode.AUTO) {
            if (!looping) {
                mode = Mode.MANUAL
                return emptyList()
            }
            loopTime = (loopTime + delta) % FindFriendsInviteMotion.LOOP
            val clocks = FindFriendsInviteMotion.clocksAtLoopTime(loopTime)
            inviteTime = clocks.invite
            scanTime = clocks.scan
            cupTime = clocks.cup
            cupTapTime = Cup.end
            return emptyList()
        }

        val haptics = mutableListOf<FindFriendsInviteHaptic>()
        var busy = false

        val oldInvite = inviteTime
        if (oldInvite > 0.0 && oldInvite < inviteEnd) {
            var nextInvite = minOf(oldInvite + delta, inviteEnd)
            if (!reduced && inviteHeld && nextInvite > Invite.release - 0.001) {
                nextInvite = maxOf(oldInvite, Invite.release - 0.001)
            }
            val launch = if (reduced) Reduced.inviteEnd / 2.0 else Invite.release + Fold.launch
            if (oldInvite < launch && nextInvite >= launch) {
                haptics += FindFriendsInviteHaptic.LIGHT
            }
            if (!reduced && oldInvite < FindFriendsInviteMotion.gustAfterPress && nextInvite >= FindFriendsInviteMotion.gustAfterPress) {
                cupTime = 0.0001
            }
            inviteTime = nextInvite
            busy = true
        }

        val oldScan = scanTime
        if (oldScan > 0.0 && oldScan < scanEnd) {
            var nextScan = minOf(oldScan + delta, scanEnd)
            if (!reduced && scanHeld && nextScan > Scan.release - 0.001) {
                nextScan = maxOf(oldScan, Scan.release - 0.001)
            }
            val done = if (reduced) Reduced.scanEnd / 2.0 else Scan.fillStart + Scan.fill
            if (oldScan < done && nextScan >= done) {
                haptics += FindFriendsInviteHaptic.SUCCESS
            }
            scanTime = nextScan
            busy = true
        }

        if (cupTime > 0.0 && cupTime < Cup.end) {
            cupTime = minOf(cupTime + delta, Cup.end)
            busy = true
        }
        if (cupTapTime < Cup.end) {
            cupTapTime = minOf(cupTapTime + delta, Cup.end)
            busy = true
        }

        if (busy || inviteHeld || scanHeld || !looping) {
            idleSinceNanos = -1L
            return haptics
        }

        if (idleSinceNanos < 0L) {
            idleSinceNanos = nowNanos
        } else if ((nowNanos - idleSinceNanos) / 1_000_000_000.0 >= FindFriendsInviteMotion.RESUME_AFTER) {
            idleSinceNanos = -1L
            loopTime = 0.0
            inviteTime = 0.0
            scanTime = 0.0
            cupTime = Cup.end
            cupTapTime = Cup.end
            mode = Mode.AUTO
        }
        return haptics
    }

    fun inviteDown(nowNanos: Long) {
        takeManualControl(nowNanos)
        if (!isRest(inviteTime, inviteEnd)) return
        inviteTime = 0.0001
        inviteHeld = true
    }

    fun inviteUp() {
        inviteHeld = false
    }

    fun scanDown(nowNanos: Long) {
        takeManualControl(nowNanos)
        if (!isRest(scanTime, scanEnd)) return
        scanTime = 0.0001
        scanHeld = true
    }

    fun scanUp() {
        scanHeld = false
    }

    fun cupTap(nowNanos: Long): FindFriendsInviteHaptic? {
        takeManualControl(nowNanos)
        if (reduced || cupTapTime < Cup.tapRearm) return null
        cupTapTime = 0.0
        return FindFriendsInviteHaptic.LIGHT
    }

    private fun takeManualControl(nowNanos: Long) {
        mode = Mode.MANUAL
        idleSinceNanos = -1L
        lastFrameNanos = nowNanos
    }

    private fun isRest(value: Double, end: Double): Boolean {
        return value <= 0.0 || value >= end
    }
}
