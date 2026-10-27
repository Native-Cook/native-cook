package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Thrown out of a spring step to stop it at its target, like Reanimated's `overshootClamping`. */
private class ConfettiGiftOfferSpringClamped : CancellationException("clamped")

/**
 * A number that can be set at once or driven by a timing or a spring, like a Reanimated shared
 * value. Reading [value] inside a `graphicsLayer {}` or draw lambda redraws only that layer.
 * Setting or starting a new animation cancels the running one, as `set` and `cancelAnimation` do.
 */
internal class ConfettiGiftOfferValue(initial: Float) {
    var value: Float by mutableFloatStateOf(initial)
        private set

    private var job: Job? = null

    /** `sv.set(next)`: jump there and stop whatever was running. */
    fun set(next: Float) {
        stop()
        value = next
    }

    /** `cancelAnimation(sv)`: keep the current value. */
    fun stop() {
        job?.cancel()
        job = null
    }

    /** `withTiming(target, { duration, easing })`; [onFinished] runs only when it completes. */
    fun timing(
        scope: CoroutineScope,
        target: Float,
        durationMs: Double,
        easing: Easing,
        onFinished: (() -> Unit)? = null,
    ) {
        stop()
        val start = value
        val millis = durationMs.toInt()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (millis <= 0) {
                withFrameNanos { }
            } else {
                animate(start, target, animationSpec = tween(millis, easing = easing)) { next, _ -> value = next }
            }
            value = target
            onFinished?.invoke()
        }
    }

    /**
     * `withSpring(target, { duration, dampingRatio, velocity, overshootClamping })`.
     * Duration and damping ratio become a stiffness the way Reanimated 4.5 solves it ([reanimatedStiffness]).
     */
    fun spring(
        scope: CoroutineScope,
        target: Float,
        durationSeconds: Double,
        dampingRatio: Float,
        velocity: Float = 0f,
        overshootClamping: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ) {
        stop()
        val start = value
        val stiffness = reanimatedStiffness(durationSeconds, dampingRatio.toDouble(), (start - target).toDouble(), velocity.toDouble()).toFloat()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                animate(
                    initialValue = start,
                    targetValue = target,
                    initialVelocity = velocity,
                    animationSpec = spring(dampingRatio = dampingRatio, stiffness = stiffness),
                ) { next, _ ->
                    val crossed = overshootClamping && ((start < target && next >= target) || (start > target && next <= target))
                    if (crossed) {
                        value = target
                        throw ConfettiGiftOfferSpringClamped()
                    }
                    value = next
                }
            } catch (clamped: ConfettiGiftOfferSpringClamped) {
                // The spring reached its target at speed: that counts as finished.
            }
            value = target
            onFinished?.invoke()
        }
    }

    /** `withDelay(delay, withTiming(target, { duration: 0 }))`. */
    fun delayedSet(scope: CoroutineScope, target: Float, delayMs: Long) {
        stop()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (delayMs > 0) delay(delayMs) else withFrameNanos { }
            value = target
        }
    }
}

/**
 * `calculateNewStiffnessToMatchDuration` of Reanimated 4.5 (springUtils.ts): `duration` is perceptual, so the
 * stiffness is the one whose energy has fallen to 6e-9 of the start after 1.5 x duration, found by the same
 * bisection between 0 and 8000 (400 ms at 0.8 gives 653.4, 300 ms at 1 gives 755.4), for a unit mass.
 */
private fun reanimatedStiffness(durationSeconds: Double, dampingRatio: Double, offset: Double, velocity: Double): Double {
    val threshold = 6e-9
    val settling = durationSeconds * 1.5
    val x0 = if (offset == 0.0 && velocity == 0.0) 1.0 else offset
    fun energyDifference(stiffness: Double): Double {
        val decay = kotlin.math.sqrt(stiffness) * dampingRatio
        val envelope = kotlin.math.exp(-decay * settling)
        val position = (x0 + (velocity + x0 * decay) * settling) * envelope
        val speed = position * -decay + (velocity + x0 * decay) * envelope
        val start = 0.5 * stiffness * x0 * x0 + 0.5 * velocity * velocity
        val end = 0.5 * stiffness * position * position + 0.5 * speed * speed
        return end / start - threshold
    }
    var low = Math.ulp(1.0)
    var high = 8000.0
    val direction = if (energyDifference(high) >= energyDifference(low)) 1.0 else -1.0
    var current = (low + high) / 2
    var iterations = 100
    while (kotlin.math.abs(energyDifference(current)) > threshold * 1e-3 && iterations > 0) {
        iterations -= 1
        if (energyDifference(current) * direction < 0) low = current else high = current
        current = (low + high) / 2
    }
    return current
}
