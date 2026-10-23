package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

import androidx.compose.animation.core.Easing
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Reanimated's shared values, withSpring, withTiming, withDelay and withSequence, stepped one frame at a
// time. The spring is Reanimated 4's own (closed-form, energy-threshold rest), so a release settles the way
// it does in the React Native block.

/** The time of the frame being run; an animation set now starts from it. */
internal class FrameTime {
    var nowMs = 0.0
}

/** One running animation. [current] is its value after the last frame. */
internal abstract class AnimRun {
    var current = 0f
    abstract fun start(value: Float, now: Double)

    /** True when it has finished. */
    abstract fun frame(now: Double): Boolean

    /** Callbacks of the leaves that finished this frame (a callback never runs for an interrupted animation). */
    open fun takeDone(out: MutableList<() -> Unit>) {}
}

private const val ENERGY_THRESHOLD = 6e-9

private fun energy(displacement: Double, velocity: Double, stiffness: Double, mass: Double): Double =
    0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity

/** withSpring: mass, stiffness and damping, or a perceptual duration and damping ratio. */
internal class SpringRun(
    private val to: Float,
    private val config: SpringConfig,
    private val velocity: Float = 0f,
    private val onFinished: (() -> Unit)? = null,
) : AnimRun() {
    private var position = 0.0
    private var speed = 0.0
    private var lastTimestamp = 0.0
    private var zeta = 0.0
    private var omega0 = 0.0
    private var omega1 = 0.0
    private var stiffness = 0.0
    private var mass = 0.0
    private var initialEnergy = 0.0
    private var finished = false

    override fun start(value: Float, now: Double) {
        position = value.toDouble()
        current = value
        mass = config.mass.toDouble()
        val x0 = (value - to).toDouble()
        // Velocity that would drive the first frame away from the target is dropped.
        var v = velocity.toDouble()
        if ((to > value && v < 0.0) || (to < value && v > 0.0)) v = 0.0
        speed = v
        stiffness = if (config.useDuration) stiffnessForDuration(x0, v) else config.stiffness.toDouble()
        if (config.useDuration) {
            zeta = config.dampingRatio.toDouble()
            omega0 = sqrt(stiffness / mass)
            omega1 = if (zeta < 1.0) omega0 * sqrt(1.0 - zeta * zeta) else 0.0
        } else {
            zeta = config.damping / (2.0 * sqrt(stiffness * mass))
            omega0 = sqrt(stiffness / mass)
            omega1 = if (zeta < 1.0) omega0 * sqrt(1.0 - zeta * zeta) else 0.0
        }
        initialEnergy = energy(x0, velocity.toDouble(), stiffness, mass)
        lastTimestamp = now
    }

    override fun frame(now: Double): Boolean {
        val dt = min(max(now - lastTimestamp, 0.0), 64.0)
        lastTimestamp = now
        val t = dt / 1000.0
        val v0 = speed
        val x0 = position - to
        if (zeta < 1.0) {
            val sin1 = sin(omega1 * t)
            val cos1 = cos(omega1 * t)
            val envelope = exp(-zeta * omega0 * t)
            val fragment = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            position = to + fragment
            speed = -zeta * omega0 * fragment + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            val envelope = exp(-omega0 * t)
            position = to + envelope * (x0 + (v0 + omega0 * x0) * t)
            speed = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
        }
        current = position.toFloat()
        val currentEnergy = energy(to - position, speed, stiffness, mass)
        if (initialEnergy == 0.0 || currentEnergy / initialEnergy <= ENERGY_THRESHOLD) {
            speed = 0.0
            current = to
            finished = true
            return true
        }
        return false
    }

    override fun takeDone(out: MutableList<() -> Unit>) {
        if (finished && onFinished != null) {
            out.add(onFinished)
            finished = false
        }
    }

    /** Reanimated's bisection for the stiffness whose settling time matches the requested duration. */
    private fun stiffnessForDuration(x0: Double, v0: Double): Double {
        val zetaTarget = config.dampingRatio.toDouble()
        val m = config.mass.toDouble()
        val energyDiff = { k: Double ->
            val settling = config.durationMs.toDouble() * 1.5 / 1000.0
            val w = sqrt(k / m) * zetaTarget
            val xtk = (x0 + (v0 + x0 * w) * settling) * exp(-w * settling)
            val vtk = (x0 + (v0 + x0 * w) * settling) * exp(-w * settling) * -w + (v0 + x0 * w) * exp(-w * settling)
            val e0 = energy(x0, v0, k, m)
            val etk = energy(xtk, vtk, k, m)
            etk / e0 - ENERGY_THRESHOLD
        }
        val precision = ENERGY_THRESHOLD * 1e-3
        var low = Math.ulp(1.0)
        var high = 8e3
        val direction = if (energyDiff(high) >= energyDiff(low)) 1 else -1
        var index = 100
        var mid = (high + low) / 2.0
        while (abs(energyDiff(mid)) > precision && index > 0) {
            index -= 1
            if (energyDiff(mid) * direction < 0) {
                low = mid
            } else {
                high = mid
            }
            mid = (low + high) / 2.0
        }
        return mid
    }
}

/** withTiming. */
internal class TimingRun(
    private val to: Float,
    private val durationMs: Float,
    private val easing: Easing = InOutQuad,
    private val onFinished: (() -> Unit)? = null,
) : AnimRun() {
    private var startValue = 0f
    private var startTime = 0.0
    private var finished = false

    override fun start(value: Float, now: Double) {
        current = value
        startValue = value
        startTime = now
    }

    override fun frame(now: Double): Boolean {
        val runtime = now - startTime
        if (runtime >= durationMs) {
            current = to
            finished = true
            return true
        }
        val progress = easing.transform((runtime / durationMs).toFloat())
        current = startValue + (to - startValue) * progress
        return false
    }

    override fun takeDone(out: MutableList<() -> Unit>) {
        if (finished && onFinished != null) {
            out.add(onFinished)
            finished = false
        }
    }
}

/** Reanimated's default timing curve: ease in and out, quadratic. */
internal val InOutQuad = Easing { t -> if (t < 0.5f) 2f * t * t else -1f + (4f - 2f * t) * t }

/** withDelay: holds the value, then runs [inner]. */
internal class DelayRun(private val delayMs: Float, private val inner: AnimRun) : AnimRun() {
    private var startTime = 0.0
    private var started = false

    override fun start(value: Float, now: Double) {
        startTime = now
        started = false
        current = value
    }

    override fun frame(now: Double): Boolean {
        if (now - startTime >= delayMs) {
            if (!started) {
                inner.start(current, now)
                started = true
            }
            val finished = inner.frame(now)
            current = inner.current
            return finished
        }
        return false
    }

    override fun takeDone(out: MutableList<() -> Unit>) = inner.takeDone(out)
}

/** withSequence: the animations one after the other, each starting from where the last ended. */
internal class SequenceRun(private val steps: List<AnimRun>) : AnimRun() {
    private var index = 0
    private val done = ArrayList<() -> Unit>(2)

    override fun start(value: Float, now: Double) {
        index = 0
        current = value
        steps[0].start(value, now)
    }

    override fun frame(now: Double): Boolean {
        val step = steps[index]
        val finished = step.frame(now)
        current = step.current
        if (finished) {
            step.takeDone(done)
            index += 1
            if (index < steps.size) {
                steps[index].start(step.current, now)
                return false
            }
            return true
        }
        return false
    }

    override fun takeDone(out: MutableList<() -> Unit>) {
        out.addAll(done)
        done.clear()
    }
}

/** A shared value: set outright, or animated by one of the runs above. */
internal class Anim(initial: Float, private val time: FrameTime) {
    var value = initial
        private set
    private var run: AnimRun? = null
    private val callbacks = ArrayList<() -> Unit>(2)

    val running: Boolean get() = run != null

    /** A plain assignment cancels any animation. */
    fun set(v: Float) {
        run = null
        value = v
    }

    /** cancelAnimation: stops where it is. */
    fun cancel() {
        run = null
    }

    fun animate(r: AnimRun) {
        r.start(value, time.nowMs)
        run = r
    }

    fun step(now: Double) {
        val r = run ?: return
        val finished = r.frame(now)
        value = r.current
        if (finished) run = null
        r.takeDone(callbacks)
        if (callbacks.isNotEmpty()) {
            val pending = callbacks.toList()
            callbacks.clear()
            pending.forEach { it() }
        }
    }
}
