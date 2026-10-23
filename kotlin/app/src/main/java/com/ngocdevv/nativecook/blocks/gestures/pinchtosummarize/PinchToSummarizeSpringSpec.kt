package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import androidx.compose.animation.core.FloatAnimationSpec
import androidx.compose.animation.core.FloatSpringSpec
import kotlin.math.sqrt

/**
 * Compose's stock scalar spring stops at a 0.01 visibility threshold. Reanimated instead stops
 * when total mechanical energy falls to 6e-9 of its initial value. This wrapper keeps Compose's
 * analytic spring solution and replaces only duration/termination with Reanimated's criterion.
 */
internal class PtsEnergySpringSpec(
    private val config: PtsSpring.Config,
    private val configuredVelocity: Float,
) : FloatAnimationSpec {
    private val normalizedStiffness = config.stiffness / config.mass
    private val dampingRatio = config.damping /
        (2f * sqrt(config.stiffness * config.mass))
    private val spring = FloatSpringSpec(
        dampingRatio = dampingRatio,
        stiffness = normalizedStiffness,
        // The delegate threshold affects only its own duration estimate; values and velocities
        // remain the analytic solution, and this wrapper supplies the duration.
        visibilityThreshold = 1f,
    )

    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float = spring.getValueFromNanos(
        playTimeNanos = playTimeNanos,
        initialValue = initialValue,
        targetValue = targetValue,
        initialVelocity = effectiveVelocity(initialValue, targetValue, initialVelocity),
    )

    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float = spring.getVelocityFromNanos(
        playTimeNanos = playTimeNanos,
        initialValue = initialValue,
        targetValue = targetValue,
        initialVelocity = effectiveVelocity(initialValue, targetValue, initialVelocity),
    )

    override fun getEndVelocity(
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float = 0f

    override fun getDurationNanos(
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Long {
        val displacement = (initialValue - targetValue).toDouble()
        // Reanimated clips the velocity used by the solution when it initially points away from
        // the target, while its initial-energy denominator retains the configured velocity.
        val solutionVelocity = effectiveVelocity(
            initialValue,
            targetValue,
            initialVelocity,
        ).toDouble()
        val initialEnergy = energy(displacement, configuredVelocity.toDouble())
        if (initialEnergy == 0.0) return 0L

        fun hasTerminated(milliseconds: Long): Boolean {
            val nanos = milliseconds * NANOS_PER_MILLISECOND
            val value = spring.getValueFromNanos(
                nanos,
                initialValue,
                targetValue,
                solutionVelocity.toFloat(),
            )
            val velocity = spring.getVelocityFromNanos(
                nanos,
                initialValue,
                targetValue,
                solutionVelocity.toFloat(),
            )
            val currentEnergy = energy(
                displacement = (targetValue - value).toDouble(),
                velocity = velocity.toDouble(),
            )
            return currentEnergy / initialEnergy <= ENERGY_THRESHOLD
        }

        if (hasTerminated(0L)) return 0L

        var upperMs = 1L
        while (upperMs < MAX_DURATION_MS && !hasTerminated(upperMs)) {
            upperMs *= 2L
        }
        upperMs = upperMs.coerceAtMost(MAX_DURATION_MS)
        if (!hasTerminated(upperMs)) return upperMs * NANOS_PER_MILLISECOND

        var lowerMs = 0L
        while (lowerMs + 1L < upperMs) {
            val middleMs = lowerMs + (upperMs - lowerMs) / 2L
            if (hasTerminated(middleMs)) upperMs = middleMs else lowerMs = middleMs
        }
        return upperMs * NANOS_PER_MILLISECOND
    }

    private fun effectiveVelocity(
        initialValue: Float,
        targetValue: Float,
        initialVelocity: Float,
    ): Float = when {
        targetValue > initialValue && initialVelocity < 0f -> 0f
        targetValue < initialValue && initialVelocity > 0f -> 0f
        else -> initialVelocity
    }

    private fun energy(displacement: Double, velocity: Double): Double =
        0.5 * config.stiffness * displacement * displacement +
            0.5 * config.mass * velocity * velocity

    private companion object {
        const val ENERGY_THRESHOLD = 6e-9
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MAX_DURATION_MS = 60_000L
    }
}
