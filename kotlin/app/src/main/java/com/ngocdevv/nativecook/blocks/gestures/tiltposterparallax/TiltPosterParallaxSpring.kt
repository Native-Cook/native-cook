package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import kotlin.math.ceil
import kotlin.math.max

/** A small physical spring whose constants use the same units as Reanimated. */
internal class TiltPosterParallaxSpring(
    value: Double,
    private val damping: Double,
    private val stiffness: Double,
    private val mass: Double,
) {
    var value = value
        private set

    var velocity = 0.0
        private set

    var target = value
        private set

    fun retarget(target: Double, velocity: Double? = null) {
        this.target = target
        if (velocity != null) {
            this.velocity = velocity
        }
    }

    fun snap(value: Double) {
        this.value = value
        target = value
        velocity = 0.0
    }

    fun step(frameSeconds: Double) {
        if (frameSeconds <= 0.0) {
            return
        }
        // Substeps keep the stiff press spring stable after a slow frame.
        val steps = max(1, ceil(frameSeconds * 120.0).toInt())
        val dt = frameSeconds / steps
        repeat(steps) {
            val acceleration = (-stiffness * (value - target) - damping * velocity) / mass
            velocity += acceleration * dt
            value += velocity * dt
        }
    }
}
