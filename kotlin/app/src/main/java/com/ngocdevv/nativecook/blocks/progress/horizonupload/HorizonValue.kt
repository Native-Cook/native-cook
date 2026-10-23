// Allocation-free frame value: RN timing curves and duration-fitted analytic springs.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import kotlin.math.*

internal class HorizonValue(initial: Float = 0f) {
    var value = initial; private set
    var velocity = 0f; private set
    var running = false; private set
    private var mode = 0
    private var from = initial
    private var target = initial
    private var start = 0.0
    private var duration = 0.0
    private var omega = 1.0
    private var damping = 1.0
    private var initialVelocity = 0.0
    private var energy = 1.0
    private var limit = Float.MAX_VALUE

    fun set(to: Float) { value = to; velocity = 0f; running = false }
    fun cancel() = set(value)
    fun timing(to: Float, ms: Double, now: Double, curve: Int = IN_OUT) {
        from = value; target = to; duration = ms; start = now; mode = curve
        velocity = 0f; running = true
    }
    fun spring(to: Float, ms: Double, ratio: Double, now: Double, speed: Float = velocity, bound: Float = Float.MAX_VALUE) {
        from = value; target = to; start = now; damping = ratio; initialVelocity = speed.toDouble()
        limit = bound; mode = SPRING; running = true
        val x = (from - to).toDouble()
        // Same conversion as the Ember/Referral ports: Reanimated mass=4, actual settling
        // window=1.5*perceptual duration, critical envelope energy threshold=6e-9.
        // Solve stiffness, then omega=sqrt(stiffness/mass); damping=2*zeta*sqrt(k*m).
        val time = ms * 1.5 / 1000
        var low = Math.ulp(1.0); var high = 8000.0
        repeat(100) {
            val k = (low + high) / 2
            val rate = sqrt(k / 4) * ratio
            val decay = exp(-rate * time)
            val envelope = x + (initialVelocity + x * rate) * time
            val displacement = envelope * decay
            val v = decay * (-rate * envelope + initialVelocity + x * rate)
            val remaining = (k * displacement * displacement + 4 * v * v) /
                max(k * x * x + 4 * initialVelocity * initialVelocity, 1e-30)
            if (remaining > 6e-9) low = k else high = k
        }
        omega = sqrt((low + high) / 8)
        energy = max(omega * omega * x * x + initialVelocity * initialVelocity, 1e-30)
    }
    fun step(now: Double) {
        if (!running) return
        val elapsed = max(now - start, 0.0)
        if (mode != SPRING) {
            val t = if (duration <= 0) 1.0 else (elapsed / duration).coerceIn(0.0, 1.0)
            val eased = when (mode) {
                LINEAR -> t
                OUT -> 1 - (1 - t) * (1 - t)
                else -> if (t < .5) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2
            }
            value = from + (target - from) * eased.toFloat()
            if (t >= 1) set(target)
            return
        }
        val t = elapsed / 1000
        val x = (from - target).toDouble()
        val decay = exp(-damping * omega * t)
        val frequency = omega * sqrt(max(1 - damping * damping, 0.0))
        val displacement: Double
        val speed: Double
        if (frequency > 1e-6) {
            val b = (initialVelocity + damping * omega * x) / frequency
            displacement = decay * (x * cos(frequency * t) + b * sin(frequency * t))
            speed = -damping * omega * displacement + decay * frequency * (-x * sin(frequency * t) + b * cos(frequency * t))
        } else {
            val b = initialVelocity + omega * x
            displacement = (x + b * t) * decay
            speed = b * decay - omega * displacement
        }
        value = (target + displacement.toFloat()).coerceIn(-limit, limit)
        velocity = speed.toFloat()
        if ((omega * omega * displacement * displacement + speed * speed) / energy <= 6e-9) set(target)
    }
    companion object { const val LINEAR = 0; const val IN_OUT = 1; const val OUT = 2; private const val SPRING = 3 }
}
