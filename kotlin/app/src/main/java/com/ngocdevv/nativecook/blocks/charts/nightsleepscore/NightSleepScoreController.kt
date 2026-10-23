package com.ngocdevv.nativecook.blocks.charts.nightsleepscore

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class NightSleepScoreController {
    private data class Settle(val from: Double, val to: Double, val startedAt: Double) {
        fun value(now: Double): Double {
            val elapsed = (now - startedAt).coerceIn(0.0, 1.0)
            val mass = .8; val damping = 34.0; val stiffness = 460.0
            val decay = damping / (2 * mass)
            val angular = sqrt(stiffness / mass - decay * decay)
            val offset = from - to
            return to + exp(-decay * elapsed) * (offset * cos(angular * elapsed) + decay * offset / angular * sin(angular * elapsed))
        }
        fun finished(now: Double) = now - startedAt >= .8
    }
    private data class ScrubSnapshot(
        val startedAt: Double, val selectedMinute: Double?, val lastTouch: Double?, val settle: Settle?, val quietFadeStarted: Double?,
    )

    var autoPlay = false; private set
    var quiet = false; private set
    var active = true; private set
    var visible = true; private set
    var selectedMinute: Double? = null; private set
    var fingerDown = false; private set
    private var appeared = false
    private var origin = 0.0
    private var pausedAt: Double? = null
    private var lastTouch: Double? = null
    private var settle: Settle? = null
    private var quietFadeStarted: Double? = null
    private var scrubSnapshot: ScrubSnapshot? = null

    private val isRunning get() = active && visible
    private fun logicalTime(now: Double) = pausedAt ?: now

    fun appear(autoPlay: Boolean, quiet: Boolean, at: Double) {
        if (appeared) return
        appeared = true; this.autoPlay = autoPlay; this.quiet = quiet; origin = logicalTime(at)
    }
    fun setAutoPlay(enabled: Boolean, at: Double) {
        autoPlay = enabled
        val time = logicalTime(at)
        if (enabled) { if (selectedMinute == null) origin = time else lastTouch = time } else lastTouch = null
    }
    fun setQuiet(enabled: Boolean, at: Double) {
        if (quiet == enabled) return
        val time = logicalTime(at)
        cancelHeldTouch(time)
        quiet = enabled; settle = null
        if (selectedMinute != null) { quietFadeStarted = time; lastTouch = if (autoPlay) time else null }
        else { quietFadeStarted = null; origin = time }
    }
    fun setActive(enabled: Boolean, at: Double) { if (active != enabled) { active = enabled; updateSuspension(at) } }
    fun setVisible(enabled: Boolean, at: Double, clearSelection: Boolean = false) {
        if (visible == enabled && !clearSelection) return
        if (clearSelection) reset(at)
        visible = enabled; updateSuspension(at)
    }
    fun beginScrub(minute: Double, at: Double) {
        if (fingerDown) { updateScrub(minute); return }
        val time = logicalTime(at)
        scrubSnapshot = ScrubSnapshot(time, selectedMinute, lastTouch, settle, quietFadeStarted)
        settle = null; quietFadeStarted = if (quiet) time else null
        selectedMinute = bounded(minute); fingerDown = true; lastTouch = null
    }
    fun updateScrub(minute: Double) { if (fingerDown) selectedMinute = bounded(minute) }
    fun endScrub(at: Double) {
        val minute = selectedMinute ?: return
        if (!fingerDown) return
        val time = logicalTime(at)
        fingerDown = false; scrubSnapshot = null; lastTouch = time
        if (quiet) { settle = null; quietFadeStarted = quietFadeStarted ?: time; return }
        val index = NightSleepScoreData.segmentIndex(minute)
        val midpoint = NightSleepScoreData.segmentStart(index) + NightSleepScoreData.segments[index].minutes / 2.0
        if (abs(midpoint - minute) > .01) { settle = Settle(minute, midpoint, time); selectedMinute = midpoint }
    }
    fun cancelHeldTouch(at: Double) {
        if (!fingerDown) return
        val snapshot = scrubSnapshot
        val held = snapshot?.let { max(0.0, logicalTime(at) - it.startedAt) } ?: 0.0
        fingerDown = false; selectedMinute = snapshot?.selectedMinute
        lastTouch = snapshot?.lastTouch?.plus(held)
        settle = snapshot?.settle?.let { it.copy(startedAt = it.startedAt + held) }
        quietFadeStarted = snapshot?.quietFadeStarted?.plus(held)
        if (snapshot?.selectedMinute == null) origin += held
        scrubSnapshot = null
    }
    fun select(stage: NightSleepScoreStage, at: Double) {
        val matches = NightSleepScoreData.segments.indices.filter { NightSleepScoreData.segments[it].stage == stage }
        if (matches.isEmpty()) return
        val current = selectedMinute?.let(NightSleepScoreData::segmentIndex)
        val index = if (current != null && NightSleepScoreData.segments[current].stage == stage) matches.firstOrNull { it > current } ?: matches.first() else matches.first()
        selectSegment(index, at)
    }
    fun adjustStage(forward: Boolean, at: Double) {
        val stages = NightSleepScoreStage.entries
        val currentStage = selectedMinute?.let { NightSleepScoreData.segments[NightSleepScoreData.segmentIndex(it)].stage }
        val current = currentStage?.let(stages::indexOf) ?: if (forward) -1 else stages.size
        val next = (current + (if (forward) 1 else -1) + stages.size) % stages.size
        select(stages[next], at)
    }
    fun reset(at: Double) {
        selectedMinute = null; fingerDown = false; lastTouch = null; settle = null; quietFadeStarted = null; scrubSnapshot = null; origin = logicalTime(at)
    }
    fun frame(at: Double): NightSleepScoreFrame {
        val time = logicalTime(at)
        val target = selectedMinute
        if (target != null) {
            var minute: Double = target
            settle?.let { movement -> minute = movement.value(time); if (movement.finished(time)) settle = null }
            val presence: Double
            if (quiet) presence = NightSleepScoreMotion.easeOutCubic((time - (quietFadeStarted ?: time - .12)) / .12)
            else if (autoPlay && !fingerDown && lastTouch != null && time - lastTouch!! > 2) {
                presence = 1 - NightSleepScoreMotion.easeInOut((time - lastTouch!! - 2) / .25)
                if (presence <= .000001) {
                    val touch = lastTouch!!
                    selectedMinute = null; lastTouch = null; settle = null; origin = touch + 2.25
                    return NightSleepScoreMotion.frame(max(0.0, time - origin))
                }
            } else presence = 1.0
            val index = NightSleepScoreData.segmentIndex(minute)
            return NightSleepScoreFrame(NightSleepScoreMotion.clamp(minute / NightSleepScoreData.windowMinutes), presence, index, presence == 0.0, presence, 1.0, -1.0)
        }
        if (autoPlay && !quiet && visible) return NightSleepScoreMotion.frame(max(0.0, time - origin))
        return NightSleepScoreFrame.REST
    }
    fun cadence(at: Double): NightSleepScoreCadence {
        if (!isRunning) return NightSleepScoreCadence.STOPPED
        if (fingerDown) {
            if (!quiet) return NightSleepScoreCadence.ANIMATION
            if (quietFadeStarted != null && logicalTime(at) - quietFadeStarted!! < .12) return NightSleepScoreCadence.ANIMATION
            return NightSleepScoreCadence.STOPPED
        }
        if (settle != null) return NightSleepScoreCadence.ANIMATION
        if (quiet && quietFadeStarted != null && selectedMinute != null && logicalTime(at) - quietFadeStarted!! < .12) return NightSleepScoreCadence.ANIMATION
        return if (autoPlay && !quiet) NightSleepScoreCadence.ANIMATION else NightSleepScoreCadence.STOPPED
    }
    val hasManualSelection get() = selectedMinute != null

    private fun selectSegment(index: Int, at: Double) {
        val time = logicalTime(at)
        settle = null; selectedMinute = NightSleepScoreData.segmentStart(index).toDouble(); fingerDown = false; scrubSnapshot = null
        lastTouch = if (autoPlay) time else null; quietFadeStarted = if (quiet) time else null
    }
    private fun bounded(minute: Double) = min(NightSleepScoreData.windowMinutes - .000001, max(0.0, minute))
    private fun updateSuspension(at: Double) {
        if (!isRunning) {
            cancelHeldTouch(logicalTime(at))
            if (pausedAt == null) pausedAt = at
            return
        }
        val paused = pausedAt ?: return
        val pause = max(0.0, at - paused); pausedAt = null; origin += pause
        lastTouch = lastTouch?.plus(pause); quietFadeStarted = quietFadeStarted?.plus(pause)
        settle = settle?.let { it.copy(startedAt = it.startedAt + pause) }
    }
}
