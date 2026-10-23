package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Who drives the heart: the port of the UI-thread state machine of PulseHeartCard.tsx.
 * The clock effect calls [step] once per display frame; draw lambdas read [frame]. Everything
 * runs on the main thread.
 */
internal class PulseHeartCardEngine(autoPlay: Boolean) {
    private enum class Mode { AUTO, MANUAL, RELEASED, REST }

    private var mode = if (autoPlay) Mode.AUTO else Mode.REST
    private var clock = 0.0
    private var lastTimestamp = -1.0
    private var lastStep = Double.NEGATIVE_INFINITY
    private var loopOrigin = 0.0
    private var phi = -99.0
    private var bpm = PulseHeartCardMotion.REST_BPM
    private var lastLub = -PulseHeartCardMotion.NEVER
    private var prevLub = -PulseHeartCardMotion.NEVER
    private var traceStart = 0.0
    private var traceEnd = -1.0
    private var releasedAt = -1.0
    private var selected = -1
    private var cursorOpacity = 0.0
    private var lastHapticAt = -1e9
    private var current = REST
    private var sleeping = false
    private var autoPlayEnabled = autoPlay
    private var quiet = false
    private var foreground = true

    /** Called when the engine itself clears the selection (the idle reset), so the UI can refresh its text. */
    var onSelectionCleared: (() -> Unit)? = null

    /** The time the next frame is needed, [Double.POSITIVE_INFINITY] for "only after an interaction", null for every frame. */
    var wakeAt: Double? = null
        private set

    val selectedIndex: Int get() = selected

    /** The latest frame: the beat from the last [step], the cursor as it is now. */
    val frame: PulseHeartCardFrame
        get() = PulseHeartCardFrame(
            beat = current.beat,
            tau0 = current.tau0,
            tau1 = current.tau1,
            phi = current.phi,
            traceStart = current.traceStart,
            traceEnd = current.traceEnd,
            selected = selected,
            cursorOpacity = cursorOpacity,
        )

    private fun setWake(value: Double?) {
        wakeAt = value
        sleeping = value != null
    }

    /** Advance the clock to [time] (seconds, monotonic) and compute the frame (frameWorklet). */
    fun step(time: Double) {
        if (time <= lastStep) return
        lastStep = time
        if (!foreground) {
            lastTimestamp = -1.0
            setWake(Double.POSITIVE_INFINITY)
            return
        }
        val previous = lastTimestamp
        lastTimestamp = time
        if (previous < 0) return
        // A frame after an intended sleep keeps the whole gap: the autoplay loop follows the wall clock.
        val gap = max(time - previous, 0.0)
        val dt = if (sleeping) min(gap, 60.0) else min(gap, 0.1)
        val now = clock + dt
        clock = now
        if (quiet || mode == Mode.REST) {
            setWake(Double.POSITIVE_INFINITY)
            return
        }
        val currentMode = mode
        val tau0: Double
        val tau1: Double
        val untilNext: Double
        val period: Double
        val p: Double
        val start: Double
        val end: Double
        if (currentMode == Mode.AUTO) {
            val pulse = PulseHeartCardMotion.autoplayAt(now - loopOrigin)
            tau0 = pulse.tau0
            tau1 = pulse.tau1
            untilNext = pulse.untilNext
            period = pulse.period
            p = pulse.phi
            start = pulse.traceStart
            end = pulse.traceEnd
        } else {
            if (currentMode == Mode.RELEASED) {
                val idle = now - releasedAt
                if (idle >= PulseHeartCardMotion.IDLE_RESET) {
                    reset()
                    onSelectionCleared?.invoke()
                    return
                }
                // The selection fades over the last 200 ms instead of vanishing.
                cursorOpacity = min(1.0, (PulseHeartCardMotion.IDLE_RESET - idle) / PulseHeartCardMotion.CURSOR_FADE)
            }
            period = 60 / bpm
            val before = phi
            val after = before + dt / period
            phi = after
            if (currentMode == Mode.MANUAL && floor(after) > floor(before)) {
                // The lub happened inside this frame: date it exactly.
                prevLub = lastLub
                lastLub = now - (after - floor(after)) * period
            }
            tau0 = min(now - lastLub, PulseHeartCardMotion.NEVER)
            tau1 = min(now - prevLub, PulseHeartCardMotion.NEVER)
            untilNext = if (currentMode == Mode.MANUAL) (ceil(after) - after) * period else PulseHeartCardMotion.NEVER
            p = after
            start = traceStart
            end = traceEnd
        }
        val beat = PulseHeartCardMotion.beatAt(tau0, tau1, untilNext, period)
        if (currentMode == Mode.AUTO && PulseHeartCardMotion.autoplayIdle(beat, tau0, p, start, end)) {
            // The loop's rest stretch: the pin's frame is written once, then nothing changes, so the shader
            // stops redrawing until the next beat gathers.
            if (current.phi != REST_PHI) current = REST
            val loopTime = (now - loopOrigin) % PulseHeartCardMotion.LOOP
            val wake = PulseHeartCardMotion.WAKE_LOOP_TIME
            val wait = if (loopTime < wake) wake - loopTime else PulseHeartCardMotion.LOOP - loopTime + wake
            setWake(time + wait)
            return
        }
        setWake(null)
        current = PulseHeartCardFrame(beat = beat, tau0 = tau0, tau1 = tau1, phi = p, traceStart = start, traceEnd = end)
    }

    // Interaction (resetUI, selectSampleUI, releaseSelectionUI)

    /** Back to the pin's frame; autoplay restarts its loop from t = 0. */
    fun reset() {
        selected = -1
        cursorOpacity = 0.0
        bpm = PulseHeartCardMotion.REST_BPM
        releasedAt = -1.0
        loopOrigin = clock
        mode = if (autoPlayEnabled) Mode.AUTO else Mode.REST
        current = REST
        setWake(null)
    }

    fun resetNow(time: Double) {
        step(time)
        reset()
    }

    /** What [select] decided: whether the sample changed and whether to play a selection haptic. */
    class Selection(val changed: Boolean, val haptic: Boolean)

    /**
     * Take over without a jump: continue the autoplay beat and trace if they are on screen, otherwise
     * start a fresh sweep with the pen at the left edge.
     */
    fun select(rawIndex: Int, time: Double): Selection {
        step(time)
        val index = rawIndex.coerceIn(0, PulseHeartCardMotion.samples.size - 1)
        val previous = selected
        val now = clock
        if (mode != Mode.MANUAL) {
            if (mode == Mode.AUTO) {
                val p = PulseHeartCardMotion.autoplayAt(now - loopOrigin)
                if (p.phi >= p.traceStart && p.phi <= p.traceEnd + PulseHeartCardMotion.TRACE_FADE_OUT) {
                    phi = p.phi
                    traceStart = p.traceStart
                    lastLub = now - p.tau0
                    prevLub = now - p.tau1
                } else {
                    phi = -PulseHeartCardMotion.TRACE_LEAD
                    traceStart = -PulseHeartCardMotion.TRACE_LEAD
                    lastLub = now - PulseHeartCardMotion.NEVER
                    prevLub = now - PulseHeartCardMotion.NEVER
                }
            } else if (mode == Mode.REST || phi > traceEnd + PulseHeartCardMotion.TRACE_FADE_OUT) {
                if (mode == Mode.REST) {
                    phi = -PulseHeartCardMotion.TRACE_LEAD
                    lastLub = now - PulseHeartCardMotion.NEVER
                    prevLub = now - PulseHeartCardMotion.NEVER
                }
                traceStart = phi
            }
            traceEnd = 1e6
            mode = Mode.MANUAL
        }
        selected = index
        bpm = PulseHeartCardMotion.samples[index].toDouble()
        releasedAt = -1.0
        cursorOpacity = 1.0
        setWake(null)
        var haptic = false
        if (previous != index && !quiet && now - lastHapticAt >= 0.1) {
            lastHapticAt = now
            haptic = true
        }
        return Selection(changed = previous != index, haptic = haptic)
    }

    fun release(time: Double) {
        step(time)
        if (mode != Mode.MANUAL) return
        mode = Mode.RELEASED
        releasedAt = clock
        traceEnd = phi
        setWake(null)
    }

    // Environment

    fun setQuiet(value: Boolean, time: Double) {
        step(time)
        quiet = value
        current = REST
        if (!value) loopOrigin = clock
        setWake(if (value) Double.POSITIVE_INFINITY else null)
    }

    fun setForeground(active: Boolean, time: Double) {
        foreground = active
        lastTimestamp = -1.0
        lastStep = Double.NEGATIVE_INFINITY
        if (active) {
            setWake(null)
            if (mode == Mode.MANUAL) release(time)
        } else {
            setWake(Double.POSITIVE_INFINITY)
        }
    }

    fun setAutoPlay(value: Boolean, time: Double) {
        step(time)
        autoPlayEnabled = value
        reset()
    }

    companion object {
        /** The rest frame's pen position: no autoplay or scrub frame ever has it, so it marks "already at rest". */
        const val REST_PHI = -99.0
        private val REST = PulseHeartCardFrame()
    }
}
