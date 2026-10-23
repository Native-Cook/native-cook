package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Reanimated duration-to-stiffness energy fit, then the analytic damped oscillator. */
internal class EmberSpring(private var value: Float) {
    // Oscillator state: displacement is relative to the target.
    private var target = value
    private var initialDisplacement = 0.0
    private var initialVelocity = 0.0
    private var elapsed = 0.0
    private var frequency = 1.0
    private var dampingRatio = 1.0
    private var clampOvershoot = false
    var done = true
        private set

    /** Fit the reference duration spring to its energy threshold before integrating analytically. */
    fun start(
        from: Float, to: Float, velocity: Float, duration: Float, damping: Float, clampOvershoot: Boolean = false,
    ) {
        value = from
        target = to
        initialDisplacement = (from - to).toDouble()
        initialVelocity = velocity.toDouble()
        elapsed = 0.0
        dampingRatio = damping.toDouble()
        this.clampOvershoot = clampOvershoot
        // Reanimated fit: mass 4, 1.5× settling window, energy floor 6e-9, 100 bisections.
        var lowerStiffness = Math.ulp(1.0)
        var upperStiffness = 8000.0
        val settlingTime = duration * 1.5
        repeat(100) {
            val stiffness = (lowerStiffness + upperStiffness) / 2
            val decayRate = sqrt(stiffness / 4) * dampingRatio
            val displacement =
                (initialDisplacement + (initialVelocity + initialDisplacement * decayRate) * settlingTime) * exp(
                    -decayRate * settlingTime
                )
            val velocity = -decayRate * displacement + (initialVelocity + initialDisplacement * decayRate) * exp(
                -decayRate * settlingTime
            )
            val initialEnergy = stiffness * initialDisplacement * initialDisplacement +
                    4 * initialVelocity * initialVelocity
            val ratio = (stiffness * displacement * displacement + 4 * velocity * velocity) /
                    initialEnergy.coerceAtLeast(1e-30)
            if (ratio > 6e-9) lowerStiffness = stiffness else upperStiffness = stiffness
        }
        frequency = sqrt((lowerStiffness + upperStiffness) / 8)
        done = initialDisplacement == 0.0 && initialVelocity == 0.0
    }

    /** Evaluate position and velocity without frame-rate-dependent Euler integration. */
    fun advance(dt: Float): Float {
        if (done) return value
        elapsed += dt
        val displacement: Double
        val velocity: Double
        if (dampingRatio < 1) {
            val oscillationFrequency = frequency * sqrt(1 - dampingRatio * dampingRatio)
            val coefficient = (initialVelocity + dampingRatio * frequency * initialDisplacement) / oscillationFrequency
            val decay = exp(-dampingRatio * frequency * elapsed)
            displacement = decay * (initialDisplacement * cos(oscillationFrequency * elapsed) + coefficient * sin(
                oscillationFrequency * elapsed
            ))
            velocity =
                -dampingRatio * frequency * displacement + decay * oscillationFrequency * (-initialDisplacement * sin(
                    oscillationFrequency * elapsed
                ) + coefficient * cos(
                    oscillationFrequency * elapsed,
                ))
        } else {
            val coefficient = initialVelocity + frequency * initialDisplacement
            displacement = (initialDisplacement + coefficient * elapsed) * exp(-frequency * elapsed)
            velocity = coefficient * exp(-frequency * elapsed) - frequency * displacement
        }
        val initialEnergy = frequency * frequency * initialDisplacement * initialDisplacement +
                initialVelocity * initialVelocity
        val energy = (frequency * frequency * displacement * displacement + velocity * velocity) /
                initialEnergy.coerceAtLeast(1e-30)
        done = energy <= 6e-9 || (clampOvershoot && displacement * initialDisplacement <= 0)
        value = if (done) target else target + displacement.toFloat()
        return value
    }
}
