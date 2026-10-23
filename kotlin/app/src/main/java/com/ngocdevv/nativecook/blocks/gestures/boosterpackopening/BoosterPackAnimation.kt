package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** An easing curve over 0..1. */
internal fun interface Easing {
    fun at(t: Double): Double

    companion object {
        val Linear = Easing { it }

        /** `Easing.out(Easing.quad)`. */
        val OutQuad = Easing { 1 - (1 - it) * (1 - it) }

        /** The strong ease-out the scene uses for fades and the card's exit. */
        val EaseOut = bezier(0.23, 1.0, 0.32, 1.0)

        /** A CSS-style cubic Bézier through (0,0), (x1,y1), (x2,y2), (1,1). */
        fun bezier(x1: Double, y1: Double, x2: Double, y2: Double): Easing {
            fun curve(a: Double, b: Double, t: Double) =
                ((1 - 3 * b + 3 * a) * t + (3 * b - 6 * a)) * t * t + 3 * a * t

            fun slope(a: Double, b: Double, t: Double) =
                3 * (1 - 3 * b + 3 * a) * t * t + 2 * (3 * b - 6 * a) * t + 3 * a
            return Easing { x ->
                if (x <= 0) return@Easing 0.0
                if (x >= 1) return@Easing 1.0
                var t = x
                for (step in 0 until 8) {
                    val s = slope(x1, x2, t)
                    if (abs(s) < 1e-7) break
                    t -= (curve(x1, x2, t) - x) / s
                }
                // Newton's method can leave the unit interval on a steep curve; bisect instead.
                if (t < 0 || t > 1 || abs(curve(x1, x2, t) - x) > 1e-5) {
                    var low = 0.0
                    var high = 1.0
                    t = x
                    for (step in 0 until 32) {
                        val value = curve(x1, x2, t)
                        if (abs(value - x) < 1e-6) break
                        if (value < x) low = t else high = t
                        t = (low + high) / 2
                    }
                }
                curve(y1, y2, t)
            }
        }
    }
}

/**
 * One animated number: a timing or a spring towards a target, with an optional delay, stepped by
 * the scene's clock. It follows Reanimated's rules, which the source's timings were tuned with:
 * a new animation or a plain `set` replaces the running one, a finish callback runs only when the
 * animation completes, and with Reduce Motion an animation that did not opt out ends at once.
 */
internal class Animated(
    private val clock: AnimationClock,
    initial: Double,
) {
    var value: Double = initial
        private set

    private var kind = NONE
    private var startTime = 0.0
    private var startValue = 0.0
    private var target = 0.0
    private var duration = 0.0
    private var easing: Easing = Easing.Linear
    private var onFinish: (() -> Unit)? = null
    private var delay = 0.0

    private var velocity = 0.0
    private var lastTime = 0.0
    private var zeta = 0.0
    private var omega0 = 0.0
    private var omega1 = 0.0
    private var stiffness = 0.0
    private var initialEnergy = 0.0
    private var overshootClamping = false
    private var springStart = 0.0

    init {
        clock.register(this)
    }

    val isAnimating: Boolean get() = kind != NONE

    /** `cancelAnimation`: the value stays where it is and the finish callback is dropped. */
    fun cancel() {
        kind = NONE
        onFinish = null
    }

    fun set(next: Double) {
        cancel()
        value = next
    }

    fun timing(
        to: Double,
        duration: Double,
        easing: Easing = Easing.Linear,
        delay: Double = 0.0,
        always: Boolean = false,
        onFinish: (() -> Unit)? = null,
    ) {
        cancel()
        if (clock.reducedMotion && !always) {
            // A delayed animation never starts under Reduce Motion; an immediate one lands.
            if (delay <= 0) value = to
            onFinish?.invoke()
            return
        }
        kind = TIMING
        // An animation started by a touch, between two frames, begins with the next frame, so its
        // first value is drawn. One started while a frame is being prepared begins with that frame.
        startTime = if (clock.inFrame) clock.now + delay else Double.NaN
        this.delay = delay
        startValue = value
        target = to
        this.duration = duration
        this.easing = easing
        this.onFinish = onFinish
    }

    /** `withSpring` configured by a perceptual duration and a damping ratio. */
    fun spring(
        to: Double,
        duration: Double,
        dampingRatio: Double,
        velocity: Double = 0.0,
        overshootClamping: Boolean = false,
        onFinish: (() -> Unit)? = null,
    ) {
        val inherited = if (kind == SPRING) this.velocity else 0.0
        cancel()
        if (clock.reducedMotion) {
            value = to
            onFinish?.invoke()
            return
        }
        kind = SPRING
        target = to
        this.onFinish = onFinish
        this.overshootClamping = overshootClamping
        springStart = value - to
        var start = inherited + velocity
        // Velocity that would drive the first frame away from the target is clipped.
        if ((to > value && start < 0) || (to < value && start > 0)) start = 0.0
        this.velocity = start
        stiffness = stiffnessForDuration(springStart, start, duration, dampingRatio)
        zeta = dampingRatio
        omega0 = sqrt(stiffness / SPRING_MASS)
        omega1 = if (zeta < 1) omega0 * sqrt(1 - zeta * zeta) else 0.0
        initialEnergy = energy(springStart, velocity, stiffness)
        lastTime = if (clock.inFrame) clock.now else Double.NaN
    }

    internal fun step(now: Double) {
        when (kind) {
            TIMING -> {
                if (startTime.isNaN()) startTime = now + delay
                if (now < startTime) return
                val progress = if (duration <= 0) 1.0 else (now - startTime) / duration
                if (progress >= 1) {
                    value = target
                    finish()
                } else {
                    value = startValue + (target - startValue) * easing.at(progress)
                }
            }
            SPRING -> stepSpring(now)
        }
    }

    private fun stepSpring(now: Double) {
        if (lastTime.isNaN()) lastTime = now
        val t = min(max(now - lastTime, 0.0), 64.0) / 1000
        lastTime = now
        val v0 = velocity
        val x0 = value - target
        if (zeta < 1) {
            val sin1 = sin(omega1 * t)
            val cos1 = cos(omega1 * t)
            val envelope = exp(-zeta * omega0 * t)
            val fragment = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            value = target + fragment
            velocity = -zeta * omega0 * fragment + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            val envelope = exp(-omega0 * t)
            value = target + envelope * (x0 + (v0 + omega0 * x0) * t)
            velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
        }

        var done = false
        if (overshootClamping) {
            val leftBound = if (springStart >= 0) target else target + springStart
            val rightBound = leftBound + abs(springStart)
            if (value < leftBound || value > rightBound) done = true
        }
        val current = energy(target - value, velocity, stiffness)
        if (done || initialEnergy == 0.0 || current / initialEnergy <= SPRING_ENERGY_THRESHOLD) {
            velocity = 0.0
            value = target
            finish()
        }
    }

    private fun finish() {
        val callback = onFinish
        kind = NONE
        onFinish = null
        callback?.invoke()
    }

    private companion object {
        const val NONE = 0
        const val TIMING = 1
        const val SPRING = 2
        const val SPRING_MASS = 4.0
        const val SPRING_ENERGY_THRESHOLD = 6e-9

        fun energy(displacement: Double, velocity: Double, stiffness: Double): Double =
            0.5 * stiffness * displacement * displacement + 0.5 * SPRING_MASS * velocity * velocity

        /** The stiffness whose spring has shed its energy 1.5 perceptual durations after it starts. */
        fun stiffnessForDuration(x0: Double, v0: Double, duration: Double, zeta: Double): Double {
            val settling = duration * 1.5 / 1000

            fun energyLeft(stiffness: Double): Double {
                val omega = sqrt(stiffness / SPRING_MASS) * zeta
                val decay = exp(-omega * settling)
                val xt = (x0 + (v0 + x0 * omega) * settling) * decay
                val vt = xt * -omega + (v0 + x0 * omega) * decay
                return energy(xt, vt, stiffness) / energy(x0, v0, stiffness) - SPRING_ENERGY_THRESHOLD
            }
            var low = Math.ulp(1.0)
            var high = 8e3
            val direction = if (energyLeft(high) >= energyLeft(low)) 1 else -1
            var current = (low + high) / 2
            var iterations = 100
            while (abs(energyLeft(current)) > SPRING_ENERGY_THRESHOLD * 1e-3 && iterations > 0) {
                iterations -= 1
                if (energyLeft(current) * direction < 0) low = current else high = current
                current = (low + high) / 2
            }
            return current
        }
    }
}

/** The time every animation of one scene reads, in milliseconds, advanced once per frame. */
internal class AnimationClock {
    var now = 0.0
        private set
    var reducedMotion = false

    /** True while the scene prepares a frame: animations started then begin at [now]. */
    var inFrame = false
    private val values = ArrayList<Animated>()

    fun register(value: Animated) {
        values.add(value)
    }

    fun advance(nowMs: Double) {
        now = nowMs
        for (index in values.indices) values[index].step(nowMs)
    }
}
