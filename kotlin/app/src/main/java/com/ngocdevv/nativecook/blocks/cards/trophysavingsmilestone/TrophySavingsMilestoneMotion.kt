package com.ngocdevv.nativecook.blocks.cards.trophysavingsmilestone

import kotlin.math.max

/** Frozen RN controller: progress is deployed material; pose retains the right-return branch. */
internal object TrophySavingsMilestoneMotion {
    const val LOOP = 5.0
    const val PEAK_HOLD = 0.22
    enum class Mode { IDLE, LOOP, DRAG, EVENT, WAITING, PAUSED }
    data class State(
        val mode: Mode = Mode.IDLE, val generation: Int = 0, val progress: Double = 0.0,
        val pose: Double = 0.0, val epoch: Double = 0.0, val direction: Double = 0.0,
        val contactOrigin: Double = 0.0, val contactPoseOrigin: Double = 0.0,
        val contactDirection: Double = 0.0, val contactVelocity: Double = 0.0,
        val translationOrigin: Double = 0.0, val eventOrigin: Double = 0.0,
        val eventPoseOrigin: Double = 0.0, val eventStarted: Double = 0.0,
        val eventDemo: Boolean = false, val eventVelocity: Double = 0.0,
        val eventUserPeak: Boolean = false, val hapticSent: Boolean = false,
    )
    fun clamp(value: Double, low: Double = 0.0, high: Double = 1.0) = value.coerceIn(low, high)
    fun smooth(value: Double): Double {
        val x = clamp(value)
        return x * x * x * (10 - 15 * x + 6 * x * x)
    }
    fun openDuration(origin: Double) = max(0.08, 0.58 * (1 - clamp(origin)))
    fun closeDuration(origin: Double) = 0.18 + 0.30 * clamp(origin)
    fun closeProgress(origin: Double, age: Double, velocity: Double = 0.0): Double {
        val p = clamp(origin); val duration = closeDuration(p)
        if (age >= duration) return 0.0
        val u = clamp(age / duration)
        val v = clamp(velocity, -3 * p / duration, 0.0)
        return (2 * u * u * u - 3 * u * u + 1) * p + (u * u * u - 2 * u * u + u) * duration * v
    }
    fun loop(time: Double): Double {
        val t = ((time % LOOP) + LOOP) % LOOP
        if (t < 0.6) return 0.0
        if (t < 1.7) return smooth((t - 0.6) / 1.1)
        if (t < 2.6) return 1.0
        if (t < 4.4) return 1 - smooth((t - 2.6) / 1.8)
        return 0.0
    }
    fun loopPose(time: Double): Double {
        val t = ((time % LOOP) + LOOP) % LOOP
        val p = loop(t)
        return if (t < 2.6) p else 2 - p
    }
    fun presentedPose(state: State, now: Double): Double {
        if (state.mode == Mode.LOOP) return loopPose(now - state.epoch)
        if (state.mode == Mode.EVENT) {
            val age = max(0.0, now - state.eventStarted)
            val origin = state.eventPoseOrigin
            if (state.eventDemo) {
                val opening = openDuration(state.eventOrigin)
                if (age < opening) return origin + (1 - origin) * smooth(age / opening)
                if (age < opening + PEAK_HOLD) return 1.0
                return 2 - closeProgress(1.0, age - opening - PEAK_HOLD)
            }
            val p = closeProgress(state.eventOrigin, age, state.eventVelocity)
            return if (origin >= 1) 2 - p else p
        }
        return state.pose
    }
    fun presentedProgress(state: State, now: Double): Double {
        val pose = presentedPose(state, now)
        return if (pose <= 1) pose else 2 - pose
    }
    fun peakTime(state: State) = if (state.eventDemo) openDuration(state.eventOrigin) else 0.0
    fun endTime(state: State) = if (state.eventDemo) peakTime(state) + PEAK_HOLD + closeDuration(1.0) else closeDuration(state.eventOrigin)
    fun beginEvent(state: State, progress: Double, demo: Boolean, userPeak: Boolean, now: Double, velocity: Double = 0.0): State {
        val p = clamp(progress); val currentPose = presentedPose(state, now)
        val pose = if (p <= 0.000001 && currentPose >= 1.999999) 0.0 else currentPose
        return state.copy(mode = Mode.EVENT, generation = state.generation + 1, progress = p, pose = pose,
            eventOrigin = p, eventPoseOrigin = pose, eventStarted = now, eventDemo = demo,
            eventVelocity = if (demo) 0.0 else velocity, eventUserPeak = userPeak && (demo || p >= 0.985), hapticSent = false)
    }
}
