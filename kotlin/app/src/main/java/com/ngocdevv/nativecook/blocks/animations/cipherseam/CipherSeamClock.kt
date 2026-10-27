package com.ngocdevv.nativecook.blocks.animations.cipherseam

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The block's one clock, `time` in the React Native file. Every frame of the screen is a function
 * of [time], in seconds of loop time, so a frozen [time] is a frozen frame.
 *
 * It runs one of four motions: standing still, the endless loop (`withRepeat(withTiming(LOOP))`,
 * linear), a single linear timing to a target (the replay), or the release spring of a drag.
 * Like a Reanimated shared value, setting a value or starting a motion cancels the running one, and
 * a timing's callback is called when it finishes and when it is cancelled.
 */
internal class CipherSeamClock(initialTime: Double) {
    private var timeState by mutableDoubleStateOf(initialTime)
    private var motion: Motion = Motion.Still

    /** Loop time in seconds. Reading it inside a draw or `graphicsLayer` block redraws only that block. */
    val time: Double get() = timeState

    private sealed interface Motion {
        data object Still : Motion

        /** The endless loop; [startNanos] is taken from the first frame after it was requested. */
        class Loop(var startNanos: Long) : Motion

        class Timing(
            val from: Double,
            val to: Double,
            val durationNanos: Long,
            var startNanos: Long,
            val onEnd: (() -> Unit)?,
        ) : Motion

        class Spring(
            val from: Double,
            val to: Double,
            val velocity: Double,
            var startNanos: Long,
        ) : Motion
    }

    /** `time.set(value)`: jumps to [value] and stands still. */
    fun set(value: Double) {
        stop()
        timeState = value
    }

    /** `cancelAnimation(time)`: stops where it is. */
    fun cancel() {
        stop()
    }

    /** `time.set(0); time.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: linear }), -1))`. */
    fun loop() {
        stop()
        timeState = 0.0
        motion = Motion.Loop(NOT_STARTED)
    }

    /** `time.set(withTiming(to, { duration, easing: linear }, onEnd))` from the current value. */
    fun timeTo(to: Double, durationMs: Int, onEnd: (() -> Unit)? = null) {
        stop()
        motion = Motion.Timing(timeState, to, durationMs * NANOS_PER_MS, NOT_STARTED, onEnd)
    }

    /**
     * `time.set(withSpring(target, { duration: 400, dampingRatio: 0.8, velocity, overshootClamping }))`:
     * a spring of mass 1 and stiffness 653.4 (what Reanimated 4 solves for a perceptual duration of
     * 400 ms: settled after 1.5 times that), started with [velocity] (loop seconds per second) and
     * ended as soon as it reaches [target].
     */
    fun springTo(target: Double, velocity: Double) {
        stop()
        motion = Motion.Spring(timeState, target, velocity, NOT_STARTED)
    }

    /** Stops for good when the screen leaves: no callback, the owner is gone. */
    fun dispose() {
        motion = Motion.Still
    }

    private fun stop() {
        val running = motion
        motion = Motion.Still
        if (running is Motion.Timing) running.onEnd?.invoke()
    }

    /** Moves the clock to the frame at [nanos]; called once per frame. */
    fun advance(nanos: Long) {
        when (val current = motion) {
            Motion.Still -> Unit
            is Motion.Loop -> {
                if (current.startNanos == NOT_STARTED) current.startNanos = nanos
                val elapsed = (nanos - current.startNanos) / NANOS_PER_SECOND
                timeState = elapsed % CIPHER_SEAM_LOOP
            }
            is Motion.Timing -> {
                if (current.startNanos == NOT_STARTED) current.startNanos = nanos
                val progress = ((nanos - current.startNanos).toDouble() / current.durationNanos).coerceIn(0.0, 1.0)
                timeState = current.from + (current.to - current.from) * progress
                if (progress >= 1.0) {
                    timeState = current.to
                    motion = Motion.Still
                    current.onEnd?.invoke()
                }
            }
            is Motion.Spring -> advanceSpring(current, nanos)
        }
    }

    /**
     * The closed form of an under-damped spring: displacement `d(t) = e^(-ζω₀t) (d₀ cos ω₁t + B sin ω₁t)`
     * from the target, with `B = (v₀ + ζω₀d₀) / ω₁`. It ends when it first reaches the target
     * (`overshootClamping`) or has come to rest.
     */
    private fun advanceSpring(spring: Motion.Spring, nanos: Long) {
        if (spring.startNanos == NOT_STARTED) spring.startNanos = nanos
        val t = (nanos - spring.startNanos) / NANOS_PER_SECOND
        val d0 = spring.from - spring.to
        val decay = exp(-DECAY * t)
        val cosine = cos(OMEGA_DAMPED * t)
        val sine = sin(OMEGA_DAMPED * t)
        val b = (spring.velocity + DECAY * d0) / OMEGA_DAMPED
        val displacement = decay * (d0 * cosine + b * sine)
        val speed = decay * ((b * OMEGA_DAMPED - DECAY * d0) * cosine - (d0 * OMEGA_DAMPED + DECAY * b) * sine)
        val crossed = if (spring.from < spring.to) displacement > 0.0 else displacement < 0.0
        val atRest = abs(displacement) < REST_DISPLACEMENT && abs(speed) < REST_SPEED
        if (crossed || atRest || t > SPRING_GIVE_UP_SECONDS) {
            timeState = spring.to
            motion = Motion.Still
        } else {
            timeState = spring.to + displacement
        }
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val NANOS_PER_SECOND = 1e9
        const val NOT_STARTED = Long.MIN_VALUE

        /**
         * The stiffness Reanimated's `calculateNewStiffnessToMatchDuration` gives `duration: 400` with
         * `dampingRatio: 0.8` (mass 1): the spring settles after 1.5 * 400 ms. ω₀ = √stiffness.
         */
        const val SPRING_STIFFNESS = 653.4

        /** `dampingRatio: 0.8`. */
        const val DAMPING_RATIO = 0.8
        val OMEGA = sqrt(SPRING_STIFFNESS)
        val DECAY = DAMPING_RATIO * OMEGA
        val OMEGA_DAMPED = OMEGA * sqrt(1 - DAMPING_RATIO * DAMPING_RATIO)
        const val REST_DISPLACEMENT = 0.001
        const val REST_SPEED = 0.01
        const val SPRING_GIVE_UP_SECONDS = 2.0
    }
}
