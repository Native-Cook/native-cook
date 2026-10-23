package com.ngocdevv.nativecook.blocks.splash.pillrisesplash

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

internal val PillEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
internal val PillRiseEase = CubicBezierEasing(0.6f, 0f, 0.3f, 1f)
internal val PillExitEase = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
internal val PillEaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
internal val PillLinear = Easing { it }

/** Reanimated's default timing curve, ease in and out (quadratic). */
internal val PillStandard = Easing { p -> if (p < 0.5f) 2f * p * p else 1f - (-2f * p + 2f) * (-2f * p + 2f) / 2f }

internal fun pillSeg(v: Float, a: Float, b: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)

/** Past [limit], a pull gives less and less. */
internal fun pillRubber(d: Float, limit: Float): Float {
    val a = abs(d)
    return Math.signum(d) * (if (a < limit) a else limit + (a - limit) * 0.3f)
}

/** Piecewise linear interpolation, clamped at both ends, like Reanimated's `interpolate`. */
internal fun pillLerp(x: Float, input: FloatArray, output: FloatArray): Float {
    if (x <= input[0]) return output[0]
    for (i in 1 until input.size) {
        if (x <= input[i]) {
            val p = (x - input[i - 1]) / (input[i] - input[i - 1])
            return output[i - 1] + (output[i] - output[i - 1]) * p
        }
    }
    return output[output.size - 1]
}

/**
 * A number driven the way Reanimated drives a shared value: set outright, or animated by a
 * (delayed) `withTiming` or a `withSpring({ duration, dampingRatio })`, stepped by the block's
 * clock. A finished animation hands back its callback; an interrupted one never calls it.
 */
internal class PillRiseValue(initial: Float) {
    var value = initial
        private set
    private var mode = 0 // 0 still, 1 spring, 2 timing
    private var done: (() -> Unit)? = null

    // Timing.
    private var from = 0f
    private var fromSet = false
    private var to = 0f
    private var start = 0f
    private var duration = 0f
    private var curve: Easing = PillLinear

    // Spring.
    private var velocity = 0f
    private var zeta = 1f
    private var omega0 = 0f
    private var omega1 = 0f
    private var stiffness = 0f
    private var initialEnergy = 0f
    private var clamp = false

    /** Puts the value somewhere at once; whatever was moving it stops, and its callback never runs. */
    fun set(next: Float) {
        value = next
        mode = 0
        done = null
    }

    fun stop() {
        mode = 0
        done = null
    }

    /** A timing that starts after [delay] s from wherever the value is by then. */
    fun timing(target: Float, seconds: Float, now: Float, curve: Easing, delay: Float = 0f, then: (() -> Unit)? = null) {
        mode = 2
        to = target
        start = now + delay
        duration = seconds
        this.curve = curve
        fromSet = delay <= 0f
        from = value
        done = then
    }

    fun spring(target: Float, speed: Float, seconds: Float, dampingRatio: Float, clampOvershoot: Boolean = false, then: (() -> Unit)? = null) {
        var v = speed
        // Speed that would carry the first frame away from the target is dropped.
        if ((target > value && v < 0f) || (target < value && v > 0f)) v = 0f
        val x0 = value - target
        mode = 1
        to = target
        velocity = v
        zeta = dampingRatio
        clamp = clampOvershoot
        stiffness = stiffnessFor(x0, v, seconds, dampingRatio)
        omega0 = sqrt(stiffness)
        omega1 = if (zeta < 1f) omega0 * sqrt(1f - zeta * zeta) else 0f
        initialEnergy = energy(x0, v)
        done = then
    }

    /** Steps to block time [now]; returns the callback of an animation that just finished. */
    fun step(dt: Float, now: Float): (() -> Unit)? {
        var finished = false
        when (mode) {
            0 -> return null
            1 -> {
                if (initialEnergy == 0f || !initialEnergy.isFinite()) {
                    value = to
                    finished = true
                } else {
                    val startValue = value
                    val x0 = value - to
                    val v0 = velocity
                    if (zeta < 1f) {
                        val sin1 = sin(omega1 * dt)
                        val cos1 = cos(omega1 * dt)
                        val envelope = exp(-zeta * omega0 * dt)
                        val frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
                        value = to + frag
                        velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
                    } else {
                        val envelope = exp(-omega0 * dt)
                        value = to + envelope * (x0 + (v0 + omega0 * x0) * dt)
                        velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
                    }
                    if (clamp && (startValue - to) * (value - to) < 0f) {
                        value = to
                        finished = true
                    } else if (energy(to - value, velocity) / initialEnergy <= ENERGY_THRESHOLD) {
                        value = to
                        finished = true
                    }
                }
            }
            2 -> {
                if (now < start) return null
                if (!fromSet) {
                    from = value
                    fromSet = true
                }
                val p = if (duration <= 0f) 1f else ((now - start) / duration).coerceIn(0f, 1f)
                value = from + (to - from) * curve.transform(p)
                if (p >= 1f) finished = true
            }
        }
        if (!finished) return null
        mode = 0
        velocity = 0f
        val callback = done
        done = null
        return callback
    }

    private fun energy(x: Float, v: Float) = 0.5f * stiffness * x * x + 0.5f * v * v

    private companion object {
        const val ENERGY_THRESHOLD = 6e-9f

        /** The stiffness Reanimated solves for so a spring of mass 1 settles in 1.5 × the duration. */
        fun stiffnessFor(x0: Float, v0: Float, seconds: Float, zeta: Float): Float {
            if (x0 == 0f && v0 == 0f) return 0f
            val settling = seconds * 1.5
            fun diff(k: Double): Double {
                val omega = sqrt(k) * zeta
                val a = x0 + (v0 + x0 * omega) * settling
                val e = exp(-omega * settling)
                val xt = a * e
                val vt = a * e * -omega + (v0 + x0 * omega) * e
                val e0 = 0.5 * k * x0 * x0 + 0.5 * v0 * v0
                val et = 0.5 * k * xt * xt + 0.5 * vt * vt
                return et / e0 - ENERGY_THRESHOLD
            }
            var lo = Math.ulp(1.0)
            var hi = 8e3
            val direction = if (diff(hi) >= diff(lo)) 1 else -1
            var current = (lo + hi) / 2
            var left = 100
            while (abs(diff(current)) > ENERGY_THRESHOLD * 1e-3 && left > 0) {
                left -= 1
                if (diff(current) * direction < 0) lo = current else hi = current
                current = (lo + hi) / 2
            }
            return if (current.isFinite()) current.toFloat() else 170f
        }
    }
}

/** A coin in flight: when it appeared (block time, s), the jar it falls into and its sideways offset. */
internal class PillRiseCoin(val t0: Float, val jar: Int, val dx: Float, val user: Boolean)

internal val PillNoCoin = PillRiseCoin(-100f, 0, 0f, false)
