package com.ngocdevv.nativecook.blocks.gestures.receiptprinter

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** A mutable scalar owned by the main-thread engine; no Compose state is read while calculating a frame. */
internal class ReceiptPrinterValue(initial: Float) {
    var value: Float = initial
        private set
    private var from = initial
    private var target = initial
    private var elapsed = 0f
    private var duration = 0f
    private var easing = Easing.OUT
    private var spring = false
    private var velocity = 0f

    enum class Easing { LINEAR, OUT, IN_OUT, OUT_QUAD, IN_OUT_QUAD }

    fun snap(next: Float) {
        value = next
        from = next
        target = next
        duration = 0f
        velocity = 0f
        spring = false
    }

    fun to(next: Float, milliseconds: Float, curve: Easing = Easing.OUT) {
        from = value
        target = next
        duration = milliseconds.coerceAtLeast(1f)
        elapsed = 0f
        easing = curve
        spring = false
    }

    fun springTo(next: Float, stiffness: Float, damping: Float, mass: Float = 1f) {
        target = next
        spring = true
        duration = 0f
        velocity = 0f
        springStiffness = stiffness
        springDamping = damping
        springMass = mass
    }

    /** Reanimated's duration spring: resolve stiffness by energy at duration × 1.5. */
    fun durationSpringTo(next: Float, milliseconds: Float, dampingRatio: Float) {
        val x0 = value - next
        if (abs(x0) < 0.00001f) {
            snap(next)
            return
        }
        val t = milliseconds * 1.5f / 1000f
        val threshold = 6e-9f
        var low = 0.000001f
        var high = 8000f
        repeat(100) {
            val k = (low + high) * 0.5f
            val omega = sqrt(k) * dampingRatio
            val e = exp(-omega * t)
            val xt = (x0 + x0 * omega * t) * e
            val vt = x0 * omega * e * (1f - omega * t) - omega * x0 * e
            val fraction = (0.5f * k * xt * xt + 0.5f * vt * vt) / (0.5f * k * x0 * x0)
            if (fraction > threshold) low = k else high = k
        }
        val stiffness = (low + high) * 0.5f
        val damping = 2f * dampingRatio * sqrt(stiffness)
        springTo(next, stiffness, damping)
    }

    private var springStiffness = 320f
    private var springDamping = 20f
    private var springMass = 1f

    fun step(milliseconds: Float) {
        if (spring) {
            val dt = milliseconds.coerceAtLeast(0f) / 1000f
            val displacement = value - target
            val alpha = springDamping / (2f * springMass)
            val omegaSquared = springStiffness / springMass
            val discriminant = alpha * alpha - omegaSquared
            val nextDisplacement: Float
            val nextVelocity: Float
            when {
                discriminant < -0.0001f -> {
                    val beta = sqrt(-discriminant)
                    val e = exp(-alpha * dt)
                    val c = cos(beta * dt)
                    val s = sin(beta * dt)
                    nextDisplacement = e * (displacement * c + (velocity + alpha * displacement) / beta * s)
                    nextVelocity = e * (velocity * c - (alpha * velocity + omegaSquared * displacement) / beta * s)
                }
                discriminant > 0.0001f -> {
                    val root = sqrt(discriminant)
                    val r1 = -alpha + root
                    val r2 = -alpha - root
                    val c1 = (velocity - r2 * displacement) / (r1 - r2)
                    val c2 = displacement - c1
                    val e1 = exp(r1 * dt)
                    val e2 = exp(r2 * dt)
                    nextDisplacement = c1 * e1 + c2 * e2
                    nextVelocity = r1 * c1 * e1 + r2 * c2 * e2
                }
                else -> {
                    val e = exp(-alpha * dt)
                    val b = velocity + alpha * displacement
                    nextDisplacement = e * (displacement + b * dt)
                    nextVelocity = e * (velocity - alpha * b * dt)
                }
            }
            value = target + nextDisplacement
            velocity = nextVelocity
            if (abs(value - target) < 0.0005f && abs(velocity) < 0.002f) snap(target)
            return
        }
        if (duration <= 0f) return
        elapsed = (elapsed + milliseconds).coerceAtMost(duration)
        val p = elapsed / duration
        value = from + (target - from) * when (easing) {
            Easing.LINEAR -> p
            Easing.OUT -> bezier(p, 0.23f, 1f, 0.32f, 1f)
            Easing.IN_OUT -> bezier(p, 0.77f, 0f, 0.175f, 1f)
            Easing.OUT_QUAD -> 1f - (1f - p) * (1f - p)
            Easing.IN_OUT_QUAD -> if (p < 0.5f) 2f * p * p else 1f - (-2f * p + 2f) * (-2f * p + 2f) / 2f
        }
        if (elapsed >= duration) {
            value = target
            duration = 0f
        }
    }

    val settled: Boolean get() = duration <= 0f && !spring

    companion object {
        private fun bezier(progress: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
            fun sample(a: Float, b: Float, t: Float): Float {
                val inv = 1f - t
                return 3f * inv * inv * t * a + 3f * inv * t * t * b + t * t * t
            }
            var low = 0f
            var high = 1f
            repeat(12) {
                val middle = (low + high) * 0.5f
                if (sample(x1, x2, middle) < progress) low = middle else high = middle
            }
            return sample(y1, y2, (low + high) * 0.5f)
        }
    }
}
