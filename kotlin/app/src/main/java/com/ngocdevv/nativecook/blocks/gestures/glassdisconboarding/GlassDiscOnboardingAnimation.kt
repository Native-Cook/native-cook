package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Small scalar animation runner that reproduces the reference's timing and duration-based spring. */
internal class GlassDiscOnboardingAnimation(initialValue: Float) {
    private var motion: Motion? = null
    private var currentVelocity = 0f

    var value: Float = initialValue
        private set

    fun snapTo(value: Float) {
        motion = null
        currentVelocity = 0f
        this.value = value
    }

    fun tweenTo(
        target: Float,
        durationMillis: Int,
        delayMillis: Int = 0,
        easing: Easing = Easing.ReferenceEaseOut,
        onComplete: (() -> Unit)? = null,
    ) {
        motion = TweenMotion(
            start = value,
            target = target,
            durationSeconds = durationMillis / MillisPerSecond,
            delaySeconds = delayMillis / MillisPerSecond,
            easing = easing,
            onComplete = onComplete,
        )
    }

    fun springTo(
        target: Float,
        durationMillis: Int,
        dampingRatio: Float,
        velocity: Float = 0f,
        thenTweenTarget: Float? = null,
        thenTweenMillis: Int = 0,
        thenTweenEasing: Easing = Easing.InOutCubic,
        onComplete: (() -> Unit)? = null,
    ) {
        val spring = SpringMotion(
            start = value,
            target = target,
            durationMillis = durationMillis,
            dampingRatio = dampingRatio,
            velocity = currentVelocity + velocity,
        )
        motion = if (thenTweenTarget == null) {
            CompletionMotion(spring, onComplete)
        } else {
            SequenceMotion(
                first = spring,
                secondFactory = { start ->
                    TweenMotion(
                        start = start,
                        target = thenTweenTarget,
                        durationSeconds = thenTweenMillis / MillisPerSecond,
                        delaySeconds = 0f,
                        easing = thenTweenEasing,
                        onComplete = onComplete,
                    )
                },
            )
        }
    }

    fun update(deltaSeconds: Float) {
        val running = motion ?: return
        val sample = running.update(deltaSeconds)
        value = sample.value
        currentVelocity = sample.velocity
        if (sample.finished && motion === running) {
            motion = null
            currentVelocity = 0f
            sample.onComplete?.invoke()
        }
    }

    enum class Easing {
        Linear,
        ReferenceEaseOut,
        CubicOut,
        QuadOut,
        InOutCubic,
    }

    private interface Motion {
        fun update(deltaSeconds: Float): Sample
    }

    private data class Sample(
        val value: Float,
        val velocity: Float,
        val finished: Boolean,
        val onComplete: (() -> Unit)? = null,
    )

    private class CompletionMotion(
        private val source: Motion,
        private val onComplete: (() -> Unit)?,
    ) : Motion {
        override fun update(deltaSeconds: Float): Sample {
            val sample = source.update(deltaSeconds)
            return sample.copy(onComplete = if (sample.finished) onComplete else null)
        }
    }

    private class SequenceMotion(
        private val first: Motion,
        private val secondFactory: (Float) -> Motion,
    ) : Motion {
        private var second: Motion? = null

        override fun update(deltaSeconds: Float): Sample {
            val currentSecond = second
            if (currentSecond != null) {
                return currentSecond.update(deltaSeconds)
            }
            val firstSample = first.update(deltaSeconds)
            if (!firstSample.finished) {
                return firstSample
            }
            val next = secondFactory(firstSample.value)
            second = next
            return next.update(0f)
        }
    }

    private class TweenMotion(
        private val start: Float,
        private val target: Float,
        private val durationSeconds: Float,
        private val delaySeconds: Float,
        private val easing: Easing,
        private val onComplete: (() -> Unit)?,
    ) : Motion {
        private var elapsedSeconds = 0f

        override fun update(deltaSeconds: Float): Sample {
            elapsedSeconds += max(0f, deltaSeconds)
            if (elapsedSeconds < delaySeconds) {
                return Sample(start, 0f, false)
            }
            if (durationSeconds <= 0f) {
                return Sample(target, 0f, true, onComplete)
            }
            val progress = ((elapsedSeconds - delaySeconds) / durationSeconds).coerceIn(0f, 1f)
            val eased = ease(progress, easing)
            val value = start + (target - start) * eased
            return Sample(value, 0f, progress >= 1f, if (progress >= 1f) onComplete else null)
        }
    }

    private class SpringMotion(
        start: Float,
        private val target: Float,
        durationMillis: Int,
        private val dampingRatio: Float,
        velocity: Float,
    ) : Motion {
        private val mass = DefaultSpringMass
        private var current = start
        private var velocity = clipVelocity(start, target, velocity)
        private val startDisplacement = start - target
        private val stiffness = solveStiffness(
            displacement = startDisplacement,
            velocity = this.velocity,
            durationMillis = durationMillis,
            dampingRatio = dampingRatio,
            mass = mass,
        )
        private val omega0 = sqrt(stiffness / mass)
        private val omega1 = if (dampingRatio < 1f) {
            omega0 * sqrt(1f - dampingRatio * dampingRatio)
        } else {
            0f
        }
        private val initialEnergy = energy(startDisplacement, this.velocity, stiffness, mass)

        override fun update(deltaSeconds: Float): Sample {
            if (initialEnergy <= 0f || !initialEnergy.isFinite()) {
                return Sample(target, 0f, true)
            }
            val time = deltaSeconds.coerceIn(0f, MaximumSpringFrameSeconds)
            val displacement = current - target
            if (dampingRatio < 1f) {
                val sine = sin(omega1 * time)
                val cosine = cos(omega1 * time)
                val envelope = exp(-dampingRatio * omega0 * time)
                val fragment = envelope * (
                    sine * ((velocity + dampingRatio * omega0 * displacement) / omega1) +
                        displacement * cosine
                    )
                current = target + fragment
                velocity = -dampingRatio * omega0 * fragment + envelope * (
                    cosine * (velocity + dampingRatio * omega0 * displacement) -
                        omega1 * displacement * sine
                    )
            } else {
                val envelope = exp(-omega0 * time)
                val offset = displacement + (velocity + omega0 * displacement) * time
                current = target + envelope * offset
                velocity = envelope * -omega0 * offset + envelope * (velocity + omega0 * displacement)
            }
            val remaining = energy(target - current, velocity, stiffness, mass) / initialEnergy
            if (remaining <= EnergyThreshold || !remaining.isFinite()) {
                current = target
                velocity = 0f
                return Sample(current, velocity, true)
            }
            return Sample(current, velocity, false)
        }
    }

    companion object {
        private const val MillisPerSecond = 1000f
        private const val DefaultSpringMass = 4f
        private const val EnergyThreshold = 0.000000006f
        private const val MaximumSpringFrameSeconds = GlassDiscOnboardingTokens.PORT.maximumFrameSeconds
        private const val PerceptualDurationCoefficient = 1.5f

        private fun clipVelocity(start: Float, target: Float, velocity: Float): Float {
            if (target > start && velocity < 0f) {
                return 0f
            }
            if (target < start && velocity > 0f) {
                return 0f
            }
            return velocity
        }

        private fun solveStiffness(
            displacement: Float,
            velocity: Float,
            durationMillis: Int,
            dampingRatio: Float,
            mass: Float,
        ): Float {
            if (abs(displacement) < 0.000001f && abs(velocity) < 0.000001f) {
                return 1f
            }
            val settlingSeconds = durationMillis / MillisPerSecond * PerceptualDurationCoefficient
            var lower = Float.MIN_VALUE
            var upper = 8000f
            repeat(100) {
                val middle = (lower + upper) * 0.5f
                val difference = energyFractionAt(
                    stiffness = middle,
                    displacement = displacement,
                    velocity = velocity,
                    settlingSeconds = settlingSeconds,
                    dampingRatio = dampingRatio,
                    mass = mass,
                ) - EnergyThreshold
                if (difference > 0f) {
                    lower = middle
                } else {
                    upper = middle
                }
            }
            return (lower + upper) * 0.5f
        }

        private fun energyFractionAt(
            stiffness: Float,
            displacement: Float,
            velocity: Float,
            settlingSeconds: Float,
            dampingRatio: Float,
            mass: Float,
        ): Float {
            val dampingFrequency = sqrt(stiffness / mass) * dampingRatio
            val envelope = exp(-dampingFrequency * settlingSeconds)
            val position = (displacement + (velocity + displacement * dampingFrequency) * settlingSeconds) * envelope
            val finalVelocity = position * -dampingFrequency + (velocity + displacement * dampingFrequency) * envelope
            val initial = energy(displacement, velocity, stiffness, mass)
            if (initial <= 0f) {
                return 0f
            }
            return energy(position, finalVelocity, stiffness, mass) / initial
        }

        private fun energy(displacement: Float, velocity: Float, stiffness: Float, mass: Float): Float {
            return 0.5f * stiffness * displacement * displacement + 0.5f * mass * velocity * velocity
        }

        private fun ease(progress: Float, easing: Easing): Float {
            return when (easing) {
                Easing.Linear -> progress
                Easing.CubicOut -> 1f - (1f - progress) * (1f - progress) * (1f - progress)
                Easing.QuadOut -> 1f - (1f - progress) * (1f - progress)
                Easing.InOutCubic -> if (progress < 0.5f) {
                    4f * progress * progress * progress
                } else {
                    1f - (-2f * progress + 2f).let { it * it * it } / 2f
                }
                Easing.ReferenceEaseOut -> cubicBezier(progress, 0.23f, 1f, 0.32f, 1f)
            }
        }

        private fun cubicBezier(progress: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
            var lower = 0f
            var upper = 1f
            repeat(14) {
                val middle = (lower + upper) * 0.5f
                if (bezier(middle, x1, x2) < progress) {
                    lower = middle
                } else {
                    upper = middle
                }
            }
            return bezier((lower + upper) * 0.5f, y1, y2)
        }

        private fun bezier(time: Float, first: Float, second: Float): Float {
            val inverse = 1f - time
            return 3f * inverse * inverse * time * first + 3f * inverse * time * time * second + time * time * time
        }
    }
}
