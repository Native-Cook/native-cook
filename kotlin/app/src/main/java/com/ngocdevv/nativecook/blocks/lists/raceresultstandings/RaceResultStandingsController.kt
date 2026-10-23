package com.ngocdevv.nativecook.blocks.lists.raceresultstandings

import com.ngocdevv.nativecook.blocks.lists.raceresultstandings.RaceResultStandingsMotion as Motion
import kotlin.math.abs
import kotlin.math.max

/** Main-thread owner of the same seven scalars used by the accepted RN component. */
internal class RaceResultStandingsController(autoPlay: Boolean) {
    private enum class Channel { SWITCH, LAP, FASTEST, REPLAY, PRESS, TRACK, BACK, FADE }
    private enum class Completion { NONE, SWITCH_FORWARD, SWITCH_BACK, LAP, FASTEST, REPLAY, FADE_OUT }
    private data class Tween(
        val start: Double,
        val end: Double,
        val at: Double,
        val duration: Double,
        val eased: Boolean,
        val completion: Completion,
    )

    var frame = RaceResultStandingsFrame()
        private set
    var backPress = 0.0
        private set
    var trackPress = 0.0
        private set
    var stretch = 0.0
        private set
    var fade = 1.0
        private set
    var quiet = false
        private set
    var active = true
        private set
    var automatic = autoPlay
        private set
    var dragging = false
        private set
    private var enabled = autoPlay
    private var switching = false
    private var pendingReplay = false
    private var lightsOutHaptic = false
    private var clock = 0.0
    private var loopTime = 0.0
    private var lastStamp: Double? = null
    private var touchedAt = 0.0
    private var initialDrag = 0.0
    private var fadeTarget = 0
    private var stretchSpring: RaceResultStandingsStretch? = null
    private val tweens = mutableMapOf<Channel, Tween>()

    val shown: Int get() = if (frame.u >= 0.5) 1 - frame.from else frame.from
    val needsFrames: Boolean get() = active && (automatic || tweens.isNotEmpty() || stretchSpring != null)

    fun nextWakeMillis(): Long? {
        if (!active || quiet || !enabled || dragging || automatic) return null
        return ((Motion.RESUME_AFTER / 1000.0 - (clock - touchedAt)) * 1000).toLong().coerceAtLeast(1)
    }

    fun configure(autoPlay: Boolean, still: Boolean) {
        val wasEnabled = enabled
        enabled = autoPlay
        if (quiet != still) {
            quiet = still
            if (still) {
                val session = shown
                tweens.clear()
                switching = false
                dragging = false
                stretchSpring = null
                stretch = 0.0
                trackPress = 0.0
                fade = 1.0
                pendingReplay = false
                frame = RaceResultStandingsFrame(from = session, flFrom = session)
                automatic = false
            } else if (autoPlay) {
                restart()
            }
        }
        if (!wasEnabled && enabled && !quiet) restart()
        if (!enabled && automatic) takeover()
    }

    fun setActive(value: Boolean) {
        active = value
        lastStamp = null
    }

    fun tick(stamp: Double) {
        if (!active) {
            lastStamp = null
            return
        }
        val elapsed = lastStamp?.let { (stamp - it).coerceAtLeast(0.0) } ?: 0.0
        lastStamp = stamp
        clock += elapsed
        if (automatic) {
            loopTime = (loopTime + elapsed.coerceAtMost(0.1)) % Motion.LOOP
            frame = Motion.frameAt(loopTime)
            return
        }
        val replayBefore = frame.rp
        // Snapshot before callbacks: a completed switch may start a lap on this same clock.
        for ((channel, tween) in tweens.toMap()) {
            val progress = if (tween.duration == 0.0) 1.0 else Motion.clamp01((clock - tween.at) / tween.duration)
            val eased = if (tween.eased) Motion.easeOut(progress) else progress
            write(channel, Motion.lerp(tween.start, tween.end, eased))
            if (progress >= 1.0 && tweens[channel] === tween) {
                tweens.remove(channel)
                complete(tween.completion)
            }
        }
        stretchSpring?.let {
            stretch = it.value(clock)
            if (stretch == 0.0) stretchSpring = null
        }
        if (replayBefore < Motion.REPLAY.go && frame.rp >= Motion.REPLAY.go) {
            lightsOutHaptic = true
        }
        if (pendingReplay && !switching && shown == 0 && frame.u == 0.0) {
            pendingReplay = false
            startReplay()
        }
        if (quiet || !enabled || dragging || (clock - touchedAt) * 1000 < Motion.RESUME_AFTER) return
        if (switching || frame.lap > 0 || frame.rp > 0 || pendingReplay || frame.flU > 0 && frame.flU < 1) return
        if (shown != 0) {
            toggle()
        } else {
            val fastest = if (frame.flU >= 1) 1 - frame.flFrom else frame.flFrom
            if (fastest != 0) rollFastest() else restart()
        }
    }

    fun touchDown(target: Int) {
        takeover()
        if (quiet || target == 0) {
            return
        }
        val channel = when (target) {
            1 -> Channel.PRESS
            2 -> Channel.BACK
            else -> Channel.TRACK
        }
        if (channel != Channel.TRACK || frame.rp <= 0.0) {
            animate(channel, 1.0, Motion.PRESS.down, eased = true)
        }
    }

    fun touchUp(target: Int, successfulTap: Boolean): Boolean {
        var haptic = false
        if (target == 1 && successfulTap && frame.rp <= 0.0 && !pendingReplay) {
            if (quiet) {
                fadeTo(1 - shown)
            } else {
                toggle()
            }
            haptic = true
        }
        if (target == 3 && successfulTap && !quiet && frame.rp <= 0.0 && !pendingReplay) {
            requestReplay()
        }
        if (target != 0) {
            val channel = when (target) {
                1 -> Channel.PRESS
                2 -> Channel.BACK
                else -> Channel.TRACK
            }
            animate(channel, 0.0, Motion.PRESS.up, eased = true)
        }
        touchedAt = clock
        return haptic
    }

    fun accessibleSwitch() {
        takeover()
        if (quiet) fadeTo(1 - shown) else toggle()
        touchedAt = clock
    }

    fun accessibleReplay() {
        if (quiet) {
            return
        }
        takeover()
        if (frame.rp <= 0.0 && !pendingReplay) {
            requestReplay()
        }
        touchedAt = clock
    }

    fun consumeLightsOutHaptic(): Boolean {
        val pending = lightsOutHaptic
        lightsOutHaptic = false
        return pending
    }

    fun beginDrag(): Boolean {
        if (quiet || frame.rp > 0.0 || pendingReplay) {
            return false
        }
        dragging = true
        tweens.remove(Channel.SWITCH)
        switching = false
        if (frame.u >= 1) frame = frame.copy(from = 1 - frame.from, u = 0.0)
        initialDrag = frame.u
        return true
    }

    fun drag(translationDp: Double, widthDp: Double) {
        val span = widthDp * Motion.DRAG.span
        val raw = initialDrag + max(0.0, -translationDp) / span
        stretchSpring = null
        if (raw <= 1) {
            frame = frame.copy(u = raw)
            stretch = 0.0
        } else {
            frame = frame.copy(u = 1.0)
            val over = (raw - 1) * span
            stretch = -(over * Motion.DRAG.rubber) / (1 + over / Motion.DRAG.rubberFalloff)
        }
    }

    fun endDrag(velocityDp: Double, cancelled: Boolean): Boolean {
        if (cancelled) {
            dragging = false
            touchedAt = clock
            return false
        }
        val commit = frame.u >= Motion.DRAG.commitAt || -velocityDp > Motion.DRAG.commitVelocity
        val haptic = commit && frame.u < 1
        stretchSpring = RaceResultStandingsStretch(stretch, clock)
        settle(if (commit) 1.0 else 0.0)
        dragging = false
        touchedAt = clock
        return haptic
    }

    private fun takeover() {
        touchedAt = clock
        if (!automatic) return
        automatic = false
        frame = Motion.frameAt(loopTime).copy(press = 0.0)
        if (frame.rp > 0.0) startReplay()
    }

    private fun settle(target: Double) {
        switching = true
        animate(
            Channel.SWITCH,
            target,
            abs(target - frame.u) * Motion.SWITCH,
            completion = if (target == 1.0) Completion.SWITCH_FORWARD else Completion.SWITCH_BACK,
        )
    }

    private fun toggle() {
        if (frame.u >= 1) frame = frame.copy(from = 1 - frame.from, u = 0.0)
        settle(if (1 - shown == 1 - frame.from) 1.0 else 0.0)
    }

    private fun runLap() {
        val start = if (frame.lap > 0 && frame.lap < Motion.LAP_END) frame.lap else 0.0001
        frame = frame.copy(lap = start)
        animate(Channel.LAP, Motion.LAP_END, (Motion.LAP_END - start) * Motion.LAP, completion = Completion.LAP)
    }

    private fun startReplay() {
        if (frame.lap > 0.0) {
            tweens.remove(Channel.LAP)
            frame = frame.copy(lap = 0.0)
            rollFastest()
        }
        val start = if (frame.rp > 0.0) frame.rp else 0.0001
        frame = frame.copy(rp = start)
        animate(Channel.REPLAY, Motion.REPLAY.end, Motion.REPLAY.end - start, completion = Completion.REPLAY)
    }

    private fun requestReplay() {
        if (switching || shown != 0 || frame.u > 0.0) {
            pendingReplay = true
            if (!switching && shown != 0) {
                toggle()
            } else if (!switching) {
                settle(if (frame.from == 0) 0.0 else 1.0)
            }
            return
        }
        startReplay()
    }

    private fun rollFastest() {
        if (frame.flU >= 1) frame = frame.copy(flFrom = 1 - frame.flFrom, flU = 0.0)
        if (frame.flFrom == shown || frame.lap > 0) return
        animate(Channel.FASTEST, 1.0, (1 - frame.flU) * Motion.FL_ROLL, completion = Completion.FASTEST)
    }

    private fun fadeTo(session: Int) {
        fadeTarget = session
        animate(Channel.FADE, 0.0, Motion.REDUCED_FADE / 1000.0, eased = true, completion = Completion.FADE_OUT)
    }

    private fun restart() {
        tweens.clear()
        switching = false
        dragging = false
        stretchSpring = null
        stretch = 0.0
        backPress = 0.0
        trackPress = 0.0
        fade = 1.0
        pendingReplay = false
        lightsOutHaptic = false
        frame = RaceResultStandingsFrame()
        loopTime = 0.0
        automatic = true
    }

    private fun animate(channel: Channel, end: Double, duration: Double, eased: Boolean = false, completion: Completion = Completion.NONE) {
        val start = when (channel) {
            Channel.SWITCH -> frame.u
            Channel.LAP -> frame.lap
            Channel.FASTEST -> frame.flU
            Channel.REPLAY -> frame.rp
            Channel.PRESS -> frame.press
            Channel.TRACK -> trackPress
            Channel.BACK -> backPress
            Channel.FADE -> fade
        }
        tweens[channel] = Tween(start, end, clock, duration, eased, completion)
    }

    private fun write(channel: Channel, value: Double) {
        when (channel) {
            Channel.SWITCH -> frame = frame.copy(u = value)
            Channel.LAP -> frame = frame.copy(lap = value)
            Channel.FASTEST -> frame = frame.copy(flU = value)
            Channel.REPLAY -> frame = frame.copy(rp = value)
            Channel.PRESS -> frame = frame.copy(press = value)
            Channel.TRACK -> trackPress = value
            Channel.BACK -> backPress = value
            Channel.FADE -> fade = value
        }
    }

    private fun complete(completion: Completion) {
        when (completion) {
            Completion.SWITCH_FORWARD -> {
                switching = false
                frame = frame.copy(from = 1 - frame.from, u = 0.0)
                runLap()
            }
            Completion.SWITCH_BACK -> switching = false
            Completion.LAP -> {
                frame = frame.copy(lap = 0.0)
                rollFastest()
            }
            Completion.FASTEST -> frame = frame.copy(flFrom = 1 - frame.flFrom, flU = 0.0)
            Completion.REPLAY -> frame = frame.copy(rp = 0.0)
            Completion.FADE_OUT -> {
                frame = RaceResultStandingsFrame(from = fadeTarget, flFrom = fadeTarget)
                animate(Channel.FADE, 1.0, Motion.REDUCED_FADE / 1000.0, eased = true)
            }
            Completion.NONE -> Unit
        }
    }
}
