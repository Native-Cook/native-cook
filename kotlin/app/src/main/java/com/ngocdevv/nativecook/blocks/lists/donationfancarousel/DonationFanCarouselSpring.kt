package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * `withSpring(target, { duration, dampingRatio, velocity })` of Reanimated 4.5, which the React
 * Native block uses to settle the fan after a drag. Reanimated turns the duration into a stiffness
 * (a bisection on the spring's energy at 1.5 x the duration, with its default mass 4 and energy
 * threshold 6e-9) and then steps the closed-form solution every frame; the solution does not depend
 * on the frame length, so it is evaluated here at any time since the start.
 *
 * Times are milliseconds on the clock of the frames.
 */
internal class DonationFanCarouselSpring(
    val from: Double,
    val to: Double,
    requestedVelocity: Double,
    durationMs: Double,
    dampingRatio: Double,
    private val startedAtMs: Double,
) {
    /** True once the energy test has snapped the value to [to]. */
    var finished = false
        private set

    private val velocity: Double
    private val zeta = dampingRatio
    private val omega0: Double
    private val omega1: Double
    private val stiffness: Double
    private val initialEnergy: Double

    init {
        val x0 = from - to
        // A velocity that would drive the first frame away from the target is dropped.
        var clipped = requestedVelocity
        if ((to > from && clipped < 0) || (to < from && clipped > 0)) clipped = 0.0
        velocity = clipped
        stiffness = matchingStiffness(x0, clipped, durationMs, dampingRatio)
        omega0 = sqrt(stiffness / Mass)
        omega1 = if (dampingRatio < 1) omega0 * sqrt(1 - dampingRatio * dampingRatio) else 0.0
        // Reanimated measures the starting energy with the velocity as passed in, not the clipped one.
        initialEnergy = energy(x0, requestedVelocity, stiffness)
    }

    /** The page value at [nowMs]; [to] once the spring has settled. */
    fun valueAt(nowMs: Double): Double {
        val t = max(0.0, nowMs - startedAtMs) / 1000.0
        if (initialEnergy == 0.0) {
            finished = true
            return to
        }
        val x0 = from - to
        val sine = sin(omega1 * t)
        val cosine = cos(omega1 * t)
        val envelope = exp(-zeta * omega0 * t)
        val fragment = envelope * (sine * ((velocity + zeta * omega0 * x0) / omega1) + x0 * cosine)
        val position = to + fragment
        val speed = -zeta * omega0 * fragment +
            envelope * (cosine * (velocity + zeta * omega0 * x0) - omega1 * x0 * sine)
        val remaining = energy(to - position, speed, stiffness)
        if (remaining / initialEnergy <= EnergyThreshold) {
            finished = true
            return to
        }
        return position
    }

    private fun energy(displacement: Double, velocity: Double, stiffness: Double): Double {
        return 0.5 * stiffness * displacement * displacement + 0.5 * Mass * velocity * velocity
    }

    /** `calculateNewStiffnessToMatchDuration` with its `bisectRoot`, step for step. */
    private fun matchingStiffness(x0: Double, v0: Double, durationMs: Double, zeta: Double): Double {
        val settlingDuration = durationMs * 1.5 / 1000

        fun difference(stiffness: Double): Double {
            val omega = sqrt(stiffness / Mass) * zeta
            val decay = exp(-omega * settlingDuration)
            val xtk = (x0 + (v0 + x0 * omega) * settlingDuration) * decay
            val vtk = (x0 + (v0 + x0 * omega) * settlingDuration) * decay * -omega + (v0 + x0 * omega) * decay
            val e0 = energy(x0, v0, stiffness)
            val etk = energy(xtk, vtk, stiffness)
            return etk / e0 - EnergyThreshold
        }

        val precision = EnergyThreshold * 1e-3
        var low = Epsilon
        var high = 8e3
        val direction = if (difference(high) >= difference(low)) 1.0 else -1.0
        var iterations = 100
        var current = (high + low) / 2
        while (abs(difference(current)) > precision && iterations > 0) {
            iterations -= 1
            if (difference(current) * direction < 0) {
                low = current
            } else {
                high = current
            }
            current = (low + high) / 2
        }
        return current
    }

    private companion object {
        /** Reanimated's default spring mass and energy threshold (`GentleSpringConfig`, `defaultConfig`). */
        const val Mass = 4.0
        const val EnergyThreshold = 6e-9

        /** `Number.EPSILON`. */
        const val Epsilon = 2.220446049250313e-16
    }
}
