package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A number that is either set directly (which cancels any spring, like `value.set(x)` in
 * Reanimated) or springs to a target (`value.set(withSpring(target, config))`).
 * The spring is the closed-form damped oscillator, so it does not depend on the frame rate.
 */
internal class EnvelopeAnimatedFloat(initial: Float = 0f) {
    var value: Float = initial
        private set

    private var springing = false
    private var target = 0f
    private var from = 0f
    private var velocity0 = 0f
    private var elapsed = 0.0
    private var initialEnergy = 0.0
    private var config = EnvelopeSprings.close

    fun set(next: Float) {
        springing = false
        value = next
    }

    /** [velocity] is in units per second, like withSpring's `velocity`. */
    fun springTo(to: Float, spring: EnvelopeSpringConfig, velocity: Float = 0f) {
        springing = true
        target = to
        from = value
        // Reanimated 4 preserves inertia only when it already points toward the target.
        velocity0 = if ((to > value && velocity < 0f) || (to < value && velocity > 0f)) {
            0f
        } else {
            velocity
        }
        elapsed = 0.0
        config = spring
        val displacement = (from - target).toDouble()
        // Reanimated computes the baseline with the configured velocity, before direction clipping.
        initialEnergy = energy(displacement, velocity.toDouble(), spring)
    }

    fun step(dtSeconds: Float) {
        if (!springing) return
        elapsed += dtSeconds
        val m = config.mass.toDouble()
        val k = config.stiffness.toDouble()
        val c = config.damping.toDouble()
        val x0 = (from - target).toDouble()
        val v0 = velocity0.toDouble()
        val omega = sqrt(k / m)
        val zeta = c / (2.0 * sqrt(k * m))
        val t = elapsed
        val x: Double
        val v: Double
        if (zeta < 1.0) {
            val damped = omega * sqrt(1.0 - zeta * zeta)
            val decay = exp(-zeta * omega * t)
            val b = (v0 + zeta * omega * x0) / damped
            val cosine = cos(damped * t)
            val sine = sin(damped * t)
            x = decay * (x0 * cosine + b * sine)
            v = decay * ((b * damped - zeta * omega * x0) * cosine - (x0 * damped + zeta * omega * b) * sine)
        } else if (zeta == 1.0) {
            val decay = exp(-omega * t)
            x = (x0 + (v0 + omega * x0) * t) * decay
            v = ((v0 + omega * x0) - omega * (x0 + (v0 + omega * x0) * t)) * decay
        } else {
            val root = omega * sqrt(zeta * zeta - 1.0)
            val r1 = -zeta * omega + root
            val r2 = -zeta * omega - root
            val a = (v0 - r2 * x0) / (r1 - r2)
            val b = x0 - a
            x = a * exp(r1 * t) + b * exp(r2 * t)
            v = a * r1 * exp(r1 * t) + b * r2 * exp(r2 * t)
        }
        value = (target + x).toFloat()
        val currentEnergy = energy(x, v, config)
        if (initialEnergy == 0.0 || currentEnergy / initialEnergy <= ENERGY_THRESHOLD) {
            value = target
            springing = false
        }
    }

    private fun energy(displacement: Double, velocity: Double, spring: EnvelopeSpringConfig): Double =
        0.5 * spring.stiffness * displacement * displacement +
            0.5 * spring.mass * velocity * velocity

    private companion object {
        // Reanimated 4's default relative-energy termination threshold.
        const val ENERGY_THRESHOLD = 6e-9
    }
}
