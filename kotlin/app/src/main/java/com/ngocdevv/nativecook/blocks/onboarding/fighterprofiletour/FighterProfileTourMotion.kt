package com.ngocdevv.nativecook.blocks.onboarding.fighterprofiletour

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

// Port of motion.ts: one scene time derived from one progress value, as in the React Native block.

const val LOOP = 10f
const val WIDTH = 393f

data class StageRect(val x: Float, val y: Float, val width: Float, val height: Float)

fun clamp(value: Float, low: Float = 0f, high: Float = 1f): Float = maxOf(low, min(high, value))

fun mix(a: Float, b: Float, progress: Float): Float = a + (b - a) * progress

fun mixRect(a: StageRect, b: StageRect, p: Float) = StageRect(
    mix(a.x, b.x, p), mix(a.y, b.y, p), mix(a.width, b.width, p), mix(a.height, b.height, p),
)

private fun curve(x: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    var low = 0f
    var high = 1f
    var t = x
    for (index in 0 until 17) {
        val q = 1f - t
        val position = 3f * q * q * t * x1 + 3f * q * t * t * x2 + t * t * t
        if (position < x) low = t else high = t
        t = (low + high) / 2f
    }
    val q = 1f - t
    return 3f * q * q * t * y1 + 3f * q * t * t * y2 + t * t * t
}

fun ramp(time: Float, start: Float, end: Float, moving: Boolean = false): Float {
    val t = clamp((time - start) / (end - start))
    return if (moving) curve(t, 0.77f, 0f, 0.175f, 1f) else curve(t, 0.23f, 1f, 0.32f, 1f)
}

fun sceneTime(progress: Float, reduced: Boolean): Float {
    val p = clamp(progress)
    val t = if (p < 0.84f) p else ((1f - p) / 0.16f) * 0.68f
    return if (reduced) (if (t < 0.22f) 0f else if (t < 0.575f) 0.4f else 0.72f) else t
}

fun stepAt(time: Float): Int = if (time < 0.22f) 1 else if (time < 0.575f) 2 else 3

/** A cancelled interaction always settles to a complete, readable composition. */
fun nearestHold(progress: Float, velocity: Float = 0f): Float {
    val projectedDistance = ((velocity / 1000f) * 0.998f) / (1f - 0.998f)
    val projected = clamp(sceneTime(progress, false) + projectedDistance, 0f, 0.72f)
    return if (projected < 0.20f) 0f else if (projected < 0.56f) 0.4f else 0.72f
}

/**
 * A shared value with Reanimated's `withSpring` (duration + dampingRatio, mass 1) and `withTiming`
 * semantics. It is stepped from one frame loop, so setting or cancelling takes effect at once.
 */
internal class Motion(initial: Float) {
    var value by mutableFloatStateOf(initial)
        private set
    private var velocity = 0f
    private var mode = IDLE

    // Spring.
    private var target = 0f
    private var stiffness = 0f
    private var damping = 0f
    private var clampOvershoot = false
    private var side = 0f
    private var restDisplacement = 0.01f
    private var restSpeed = 0.01f

    // Timing.
    private var from = 0f
    private var elapsed = 0f
    private var duration = 0f
    private var easing: Easing? = null

    /** `shared.set(v)`: also cancels a running animation. */
    fun set(next: Float) {
        mode = IDLE
        velocity = 0f
        value = next
    }

    /** `cancelAnimation`: stops where it is. */
    fun cancel() {
        mode = IDLE
        velocity = 0f
    }

    fun spring(
        to: Float,
        durationMs: Float,
        dampingRatio: Float,
        initialVelocity: Float? = null,
        overshootClamping: Boolean = false,
        restDisplacementThreshold: Float = 0.01f,
        restSpeedThreshold: Float = 0.01f,
    ) {
        val omega = (2.0 * Math.PI / (durationMs / 1000f)).toFloat()
        stiffness = omega * omega
        damping = 2f * dampingRatio * sqrt(stiffness)
        if (initialVelocity != null) velocity = initialVelocity else if (mode != SPRING) velocity = 0f
        target = to
        clampOvershoot = overshootClamping
        side = Math.signum(to - value)
        restDisplacement = restDisplacementThreshold
        restSpeed = restSpeedThreshold
        mode = SPRING
    }

    fun timing(to: Float, durationMs: Float, curve: Easing) {
        from = value
        target = to
        duration = durationMs
        elapsed = 0f
        easing = curve
        velocity = 0f
        mode = TIMING
    }

    fun step(dtMs: Float) {
        when (mode) {
            SPRING -> {
                var remaining = dtMs / 1000f
                while (remaining > 0f && mode == SPRING) {
                    val h = min(remaining, 1f / 240f)
                    remaining -= h
                    val acceleration = -stiffness * (value - target) - damping * velocity
                    velocity += acceleration * h
                    value += velocity * h
                    if (clampOvershoot && (value - target) * side > 0f) {
                        finish()
                    } else if (abs(value - target) < restDisplacement && abs(velocity) < restSpeed) {
                        finish()
                    }
                }
            }
            TIMING -> {
                elapsed += dtMs
                val p = clamp(elapsed / duration)
                value = from + (target - from) * (easing?.transform(p) ?: p)
                if (p >= 1f) finish()
            }
        }
    }

    private fun finish() {
        value = target
        velocity = 0f
        mode = IDLE
    }

    private companion object {
        const val IDLE = 0
        const val SPRING = 1
        const val TIMING = 2
    }
}
