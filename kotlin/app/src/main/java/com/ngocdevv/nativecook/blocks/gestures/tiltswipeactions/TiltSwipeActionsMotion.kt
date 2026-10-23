package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reanimated's Easing.inOut(Easing.quad): the default easing of withTiming, which the reference
 * uses for the tile colour, the finger's fade, the tab switch and the reduced-motion steps.
 */
internal val EaseInOutQuad = Easing { t ->
    if (t < 0.5f) 2f * t * t else 1f - 2f * (1f - t) * (1f - t)
}

/**
 * The frame clock of every [MotionValue] of one block. The reference writes Reanimated shared
 * values from worklets (an assignment takes effect at once, withSpring/withTiming start on the next
 * frame, and a new animation replaces the running one and inherits its velocity); this keeps the
 * same semantics on Compose state so a read in a graphicsLayer or draw lambda only redraws that.
 */
internal class MotionClock {
    private val running = ArrayList<MotionValue>(16)
    private var elapsedSeconds = 0.0

    fun add(value: MotionValue) {
        if (value.queued) return
        value.queued = true
        running.add(value)
    }

    /** Advances from frame-clock deltas; clamping at the caller prevents a stall from skipping motion. */
    fun tick(deltaSeconds: Float) {
        elapsedSeconds += deltaSeconds
        var index = 0
        // A callback may start animations (appended, so they get their first tick in this pass).
        while (index < running.size) {
            val value = running[index]
            value.tick(elapsedSeconds)
            if (value.isAnimating) {
                index += 1
            } else {
                value.queued = false
                running.removeAt(index)
            }
        }
    }
}

/** A Reanimated shared value holding one Float, with the animation that currently drives it. */
internal class MotionValue(initial: Float, private val clock: MotionClock) {
    private val state = mutableFloatStateOf(initial)

    val value: Float get() = state.floatValue

    /** Velocity of the running spring in units per second, the part a new spring inherits. */
    private var velocity = 0f
    private var animation: MotionAnimation? = null
    internal var queued = false

    val isAnimating: Boolean get() = animation != null

    /** value = x: an assignment cancels the running animation (its callback gets finished = false). */
    fun set(next: Float) {
        stop(finished = false)
        state.floatValue = next
    }

    /** cancelAnimation(value): stops where it is. */
    fun cancelAnimation() {
        stop(finished = false)
    }

    /**
     * withSpring(target, { damping, stiffness, mass, velocity }), optionally behind withDelay.
     * Like Reanimated the new spring starts from the value with the previous spring's velocity
     * plus [velocity].
     */
    fun spring(
        target: Float,
        spec: TiltSpring,
        velocity: Float = 0f,
        delaySeconds: Float = 0f,
        onEnd: ((Boolean) -> Unit)? = null,
    ) {
        val initial = if (animation != null) this.velocity + velocity else velocity
        start(SpringAnimation(target, spec, initial, delaySeconds, onEnd))
    }

    /** withTiming(target, { duration, easing }). */
    fun timing(
        target: Float,
        durationMs: Int,
        easing: Easing = EaseInOutQuad,
        onEnd: ((Boolean) -> Unit)? = null,
    ) {
        // A timing animation has no velocity to hand on.
        velocity = 0f
        start(TimingAnimation(target, durationMs, easing, onEnd))
    }

    internal fun tick(nowSeconds: Double) {
        val running = animation ?: return
        if (running.step(nowSeconds)) {
            animation = null
            velocity = 0f
            running.onEnd?.invoke(true)
        }
    }

    private fun start(next: MotionAnimation) {
        val previous = animation
        animation = next
        previous?.onEnd?.invoke(false)
        clock.add(this)
    }

    private fun stop(finished: Boolean) {
        val previous = animation ?: return
        animation = null
        velocity = 0f
        previous.onEnd?.invoke(finished)
    }

    private abstract inner class MotionAnimation(val onEnd: ((Boolean) -> Unit)?) {
        var startedAt = Double.NaN

        /** Returns true when the animation is over (the value then equals the target). */
        abstract fun step(nowSeconds: Double): Boolean
    }

    private inner class TimingAnimation(
        private val target: Float,
        private val durationMs: Int,
        private val easing: Easing,
        onEnd: ((Boolean) -> Unit)?,
    ) : MotionAnimation(onEnd) {
        private var from = 0f

        override fun step(nowSeconds: Double): Boolean {
            if (startedAt.isNaN()) {
                startedAt = nowSeconds
                from = state.floatValue
            }
            val elapsedMs = (nowSeconds - startedAt) * 1000.0
            if (elapsedMs >= durationMs) {
                state.floatValue = target
                return true
            }
            val progress = easing.transform((elapsedMs / durationMs).toFloat())
            state.floatValue = from + (target - from) * progress
            return false
        }
    }

    /**
     * Reanimated's closed-form spring: m x'' = -k (x - target) - c x', started from the current
     * value, at rest when the speed is under 2 and the distance under 0.01 (its defaults).
     */
    private inner class SpringAnimation(
        private val target: Float,
        spec: TiltSpring,
        private val initialVelocity: Float,
        private val delaySeconds: Float,
        onEnd: ((Boolean) -> Unit)?,
    ) : MotionAnimation(onEnd) {
        private val omega = sqrt(spec.stiffness / spec.mass)
        private val zeta = spec.damping / (2f * sqrt(spec.stiffness * spec.mass))
        private var begun = false
        private var begunAt = 0.0
        private var offset0 = 0f

        override fun step(nowSeconds: Double): Boolean {
            if (startedAt.isNaN()) startedAt = nowSeconds
            if (!begun) {
                if (nowSeconds - startedAt < delaySeconds) return false
                begun = true
                begunAt = nowSeconds
                offset0 = state.floatValue - target
                velocity = initialVelocity
            }
            val t = (nowSeconds - begunAt).toFloat()
            val offset: Float
            val speed: Float
            if (zeta < 1f) {
                val damped = omega * sqrt(1f - zeta * zeta)
                val decay = zeta * omega
                val sine = (initialVelocity + decay * offset0) / damped
                val envelope = exp(-decay * t)
                val c = cos(damped * t)
                val s = sin(damped * t)
                offset = envelope * (offset0 * c + sine * s)
                speed = envelope * ((sine * damped - decay * offset0) * c + (-offset0 * damped - decay * sine) * s)
            } else {
                // Critically damped (an over-damped config would only be slower to settle).
                val k = initialVelocity + omega * offset0
                val envelope = exp(-omega * t)
                offset = envelope * (offset0 + k * t)
                speed = envelope * (k - omega * (offset0 + k * t))
            }
            velocity = speed
            if (abs(speed) < REST_SPEED && abs(offset) < REST_DISTANCE) {
                state.floatValue = target
                return true
            }
            state.floatValue = target + offset
            return false
        }
    }

    private companion object {
        const val REST_SPEED = 2f
        const val REST_DISTANCE = 0.01f
    }
}
