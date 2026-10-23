package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.runtime.mutableDoubleStateOf
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Reanimated 4.5.1 timing and duration springs, following ReferralValue.
// MIT, Copyright (c) 2016 Software Mansion.

/** A frame-stepped shared value. Queues allocate at interaction time, never while stepping. */
internal class AuraValue(
    initial: Double,
) {
    private val state = mutableDoubleStateOf(initial)
    var value: Double
        get() = state.doubleValue
        private set(newValue) {
            state.doubleValue = newValue
        }

    var velocity = 0.0
        private set

    // The longest choreography fits five segments; frames reuse this storage.
    private val targets = DoubleArray(queueCapacity)
    private val durations = DoubleArray(queueCapacity)
    private val ratios = DoubleArray(queueCapacity)
    private val delays = DoubleArray(queueCapacity)
    private val modes = IntArray(queueCapacity)
    private var length = 0
    private var slot = 0
    private var active = false
    private var start = 0.0
    private var last = 0.0
    private var from = initial
    private var configuredVelocity = 0.0
    private var omega = 0.0
    private var stiffness = 0.0
    private var energy = 0.0
    private var startOffset = 0.0
    val running
        get() = slot < length

    val target
        get() = if (running) targets[length - 1] else value

    fun set(newValue: Double) {
        length = 0
        slot = 0
        active = false
        velocity = 0.0
        configuredVelocity = 0.0
        value = newValue
    }

    fun cancel() = set(value)

    fun timing(
        to: Double,
        duration: Double,
        now: Double,
        delay: Double = 0.0,
        curve: Int = easeOut,
    ): AuraValue {
        length = 0
        slot = 0
        active = false
        velocity = 0.0
        configuredVelocity = 0.0
        start = now
        return append(to, duration, 0.0, delay, curve)
    }

    fun spring(
        to: Double,
        duration: Double,
        dampingRatio: Double,
        now: Double,
        delay: Double = 0.0,
        initialVelocity: Double = 0.0,
        clamp: Boolean = false,
    ): AuraValue {
        // Reanimated's isTriggeredTwice: an identical active spring retains its
        // original displacement, frequency and timestamps instead of restarting.
        if (
            delay == 0.0 &&
            running &&
            active &&
            modes[slot] >= springMode &&
            targets[slot] == to &&
            durations[slot] == duration &&
            ratios[slot] == dampingRatio
        ) {
            length = slot + 1
            modes[slot] = if (clamp) clampedSpringMode else springMode
            if ((to - value) * velocity < 0) velocity = 0.0
            configuredVelocity = initialVelocity
            // A replacement Reanimated configuration starts with default stiffness 900;
            // only its frequency is inherited in this branch (spring.ts onStart).
            stiffness = 900.0
            energy =
                0.5 * stiffness * startOffset * startOffset + 2 * initialVelocity * initialVelocity
            return this
        }
        // Retarget from the currently drawn value and retain only forward inertia.
        configuredVelocity = initialVelocity
        val initialSpeed = velocity + initialVelocity
        length = 0
        slot = 0
        active = false
        start = now
        velocity = if ((to - value) * initialSpeed < 0) 0.0 else initialSpeed
        return append(to, duration, dampingRatio, delay, if (clamp) clampedSpringMode else springMode)
    }

    fun thenTiming(
        to: Double,
        duration: Double,
        delay: Double = 0.0,
        curve: Int = easeOut,
    ) = append(to, duration, 0.0, delay, curve)

    fun thenSpring(
        to: Double,
        duration: Double,
        dampingRatio: Double,
        delay: Double = 0.0,
    ) = append(to, duration, dampingRatio, delay, springMode)

    private fun append(
        to: Double,
        duration: Double,
        dampingRatio: Double,
        delay: Double,
        mode: Int,
    ): AuraValue {
        check(length < queueCapacity)
        targets[length] = to
        durations[length] = duration
        ratios[length] = dampingRatio
        delays[length] = delay
        modes[length] = mode
        length++
        return this
    }

    fun step(now: Double) {
        if (!running || (!active && now - start < delays[slot])) return
        if (!active) startSegment(now)
        val to = targets[slot]
        val duration = durations[slot]
        val mode = modes[slot]
        val done: Boolean
        if (mode < springMode) {
            val t = if (duration == 0.0) 1.0 else ((now - start) / duration).coerceIn(0.0, 1.0)
            val progress =
                when (mode) {
                    linear -> t
                    easeInOut -> AuraEasing.bezier(t, true)
                    else -> AuraEasing.bezier(t)
                }
            value = from + (to - from) * progress
            done = t >= 1
        } else {
            done = stepSpring(now, to, mode)
        }
        last = now
        if (done) {
            value = to
            velocity = 0.0
            slot++
            active = false
            start = now
            if (running && delays[slot] <= 0) step(now)
        }
    }

    private fun startSegment(now: Double) {
        active = true
        if (delays[slot] > 0) start = now
        last = start
        from = value
        startOffset = value - targets[slot]
        if (modes[slot] >= springMode) {
            stiffness =
                stiffnessForDuration(startOffset, velocity, durations[slot], ratios[slot])
            omega = sqrt(stiffness / 4)
            energy =
                0.5 * stiffness * startOffset * startOffset +
                2 * configuredVelocity * configuredVelocity
        }
    }

    private fun stepSpring(
        now: Double,
        to: Double,
        mode: Int,
    ): Boolean {
        val dt = ((now - last).coerceIn(0.0, 64.0)) / 1000
        val damping = ratios[slot]
        val x = value - to
        val initialSpeed = velocity
        if (damping < 1) {
            val frequency = omega * sqrt(1 - damping * damping)
            val sine = sin(frequency * dt)
            val cosine = cos(frequency * dt)
            val decay = exp(-damping * omega * dt)
            val part = decay * (sine * (initialSpeed + damping * omega * x) / frequency + x * cosine)
            value = to + part
            velocity =
                -damping * omega * part +
                decay * (cosine * (initialSpeed + damping * omega * x) - frequency * x * sine)
        } else {
            val decay = exp(-omega * dt)
            val progress = x + (initialSpeed + omega * x) * dt
            value = to + decay * progress
            velocity = decay * (-omega * progress + initialSpeed + omega * x)
        }
        val offset = value - to
        return energy == 0.0 ||
            (0.5 * stiffness * offset * offset + 2 * velocity * velocity) / energy <=
            6e-9 ||
            (
                mode == clampedSpringMode &&
                    (value < min(to, to + startOffset) || value > max(to, to + startOffset))
            )
    }

    companion object {
        private val queueCapacity = 5
        val linear = 0
        val easeOut = 1
        val easeInOut = 2
        private val springMode = 4
        private val clampedSpringMode = 5

        // Solve the stiffness whose remaining energy reaches 6e-9 with mass four. The 1.5
        // duration factor and critical envelope keep duration-based springs consistent.

        fun stiffnessForDuration(
            x: Double,
            initialSpeed: Double,
            duration: Double,
            damping: Double,
        ): Double {
            if (x == 0.0 && initialSpeed == 0.0) return 900.0
            val t = duration * 1.5 / 1000
            var lower = Math.ulp(1.0)
            var upper = 8000.0
            var stiffnessGuess = (lower + upper) / 2
            var i = 100
            while (i-- > 0) {
                val frequency = sqrt(stiffnessGuess / 4) * damping
                val decay = exp(-frequency * t)
                val envelope = x + (initialSpeed + x * frequency) * t
                val displacement = envelope * decay
                val speed = decay * (-frequency * envelope + initialSpeed + x * frequency)
                val error =
                    (0.5 * stiffnessGuess * displacement * displacement + 2 * speed * speed) /
                        (0.5 * stiffnessGuess * x * x + 2 * initialSpeed * initialSpeed) -
                        6e-9
                if (abs(error) <= 6e-12) break
                if (error > 0) lower = stiffnessGuess else upper = stiffnessGuess
                stiffnessGuess = (lower + upper) / 2
            }
            return stiffnessGuess
        }
    }
}
