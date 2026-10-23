// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Reanimated's `ReduceMotion` config. */
enum class ReduceMotion { System, Always, Never }

/** Reanimated's withTiming default easing, `Easing.inOut(Easing.quad)`. */
val InOutQuad = Easing { t -> if (t < 0.5f) 2 * t * t else 1 - 2 * (1 - t) * (1 - t) }

/** An easing curve evaluated in a derived value (`Easing.bezier(...)(x)` in a worklet). */
operator fun Easing.invoke(x: Double): Double = transform(x.toFloat()).toDouble()

/**
 * Reanimated 4.5's animation objects (`src/animation/`) on Compose.
 *
 * `withTiming` and `withSpring` run as Compose `Animatable`s with native
 * `tween` / `spring` specs; what stays here is the Reanimated contract around
 * them: the decorated `onStart` (reduce-motion short-circuit), hand-over from
 * the previous animation (velocity, timeline continuity), the spring's config
 * conversions and its energy-based completion. `withDecay` (rubber band) and
 * `withDelay` remain ported steppers. Each keeps the original field names.
 */
abstract class Animation {
    /** Where an animation writes: the shared value it was assigned to. */
    interface Sink {
        fun read(): Double
        fun write(v: Double)
        /** Finished by itself (`callback(true)`). */
        fun done()
    }

    var current: Double = 0.0
    /** `undefined` in JS for withDecay; target otherwise. */
    open val toValue: Double? = null
    var callback: ((Boolean) -> Unit)? = null
    var finished = false
    var cancelled = false
    /** null = undefined (inherit from a parent or the system setting). */
    var reduceMotion: Boolean? = null
    open val isHigherOrder: Boolean = false

    /** `previousAnimation.velocity`: `undefined` (NaN) unless a spring or decay. */
    open val velocity: Double get() = Double.NaN
    /** `previousAnimation.lastTimestamp`: 0 unless a running spring or decay. */
    open val lastTimestamp: Double get() = 0.0
    /** Still producing values (not ended by itself, not detached). */
    internal abstract val running: Boolean

    protected lateinit var sink: Sink

    /** decorateAnimation(...).onStart, then the first step at `now` (valueSetter). */
    internal fun start(value: Double, now: Double, previous: Animation?, sink: Sink) {
        this.sink = sink
        if (reduceMotion == null) reduceMotion = AnimationEngine.systemReduceMotion
        if (!isHigherOrder && reduceMotion == true) {
            // Jump to the end: the first step reports it finished.
            current = toValue ?: reducedStart(value, now)
            sink.write(current)
            sink.done()
            return
        }
        launch(value, now, previous)
    }

    protected open fun reducedStart(value: Double, now: Double): Double = value
    protected abstract fun launch(value: Double, now: Double, previous: Animation?)

    /** Stops producing values, without a callback (the value setter reports it). */
    internal abstract fun detach()

    /** withDelay keeps the previous animation running until it fires. */
    internal open fun adopts(previous: Animation): Boolean = false
}

internal fun resolveReduceMotion(config: ReduceMotion?): Boolean? = when (config) {
    null -> null
    ReduceMotion.System -> AnimationEngine.systemReduceMotion
    ReduceMotion.Always -> true
    ReduceMotion.Never -> false
}

/**
 * An animation run by a Compose [Animatable] on the engine's frame clock.
 * The Animatable holds the offset from the target, so values keep double
 * precision near the target and land on it exactly, as Reanimated's do.
 */
abstract class NativeAnimation : Animation() {
    private var animatable: Animatable<Float, AnimationVector1D>? = null
    private var job: Job? = null
    private var detached = false
    private var ended = false
    private var stoppedVelocity = 0.0
    /** Time (ms) of the last frame stepped; 0 once ended (Reanimated's lastTimestamp). */
    protected var frameTime = 0.0

    override val running: Boolean get() = animatable != null && !ended && !detached

    /** The Animatable's velocity; 0 once ended, frozen once detached. */
    protected val runVelocity: Double
        get() = when {
            ended -> 0.0
            detached -> stoppedVelocity
            else -> animatable?.velocity?.toDouble() ?: 0.0
        }

    /**
     * Animates from `target + offset` to `target`. [startFrames] are the
     * times (ms) handed over synchronously: the timeline origin first, then
     * `now`. [bounds] (offset space) end the run at a bound like
     * `overshootClamping`.
     */
    protected fun run(
        target: Double,
        offset: Double,
        spec: FiniteAnimationSpec<Float>,
        initialVelocity: Double,
        startFrames: List<Double>,
        bounds: ClosedFloatingPointRange<Double>? = null,
    ) {
        val a = Animatable(offset.toFloat())
        if (bounds != null) a.updateBounds(bounds.start.toFloat(), bounds.endInclusive.toFloat())
        animatable = a
        var first = true
        AnimationEngine.launchAt(startFrames) {
            job = coroutineContext.job
            a.animateTo(0f, spec, initialVelocity.toFloat()) {
                if (detached || ended) return@animateTo
                frameTime = AnimationEngine.deliveredMs
                val x = this.value.toDouble()
                current = target + x
                when {
                    terminates(x, this.velocity.toDouble()) -> end(target)
                    // The origin frame is the start value itself.
                    first -> first = false
                    else -> sink.write(current)
                }
            }
            // Finished, or BoundReached at an overshootClamping bound.
            end(target)
        }
    }

    /** Reanimated's per-frame completion test besides the spec's own end. */
    protected open fun terminates(offset: Double, velocity: Double): Boolean = false

    protected open fun onEnd() {}

    /** Ends at the target on the first step (skipped or zero-energy springs). */
    protected fun endNow(target: Double) {
        ended = true
        animatable = null
        frameTime = 0.0
        onEnd()
        current = target
        sink.write(target)
        sink.done()
    }

    private fun end(target: Double) {
        if (ended || detached) return
        ended = true
        job?.cancel()
        frameTime = 0.0
        onEnd()
        current = target
        sink.write(target)
        sink.done()
    }

    override fun detach() {
        if (detached) return
        stoppedVelocity = runVelocity
        detached = true
        job?.cancel()
    }
}

/** withTiming: a native `tween` with the Reanimated easing as a Compose [Easing]. */
class TimingAnimation(
    private val target: Double,
    duration: Double = 300.0,
    easing: Easing = InOutQuad,
    reduce: ReduceMotion? = null,
    onDone: ((Boolean) -> Unit)? = null,
) : NativeAnimation() {
    override val toValue: Double get() = target
    var startTime = 0.0
    var startValue = 0.0
    // Reanimated durations are whole milliseconds here, as tween's are.
    private val spec = tween<Float>(durationMillis = duration.roundToInt(), easing = easing)

    init {
        require(duration == duration.roundToInt().toDouble()) { "withTiming duration must be whole ms: $duration" }
        current = target
        callback = onDone
        reduceMotion = resolveReduceMotion(reduce)
    }

    override fun launch(value: Double, now: Double, previous: Animation?) {
        if (previous is TimingAnimation && previous.toValue == target && previous.startTime != 0.0) {
            // Continuity: the same timing started again keeps its timeline.
            startTime = previous.startTime
            startValue = previous.startValue
        } else {
            startTime = now
            startValue = value
        }
        current = value
        run(target, startValue - target, spec, 0.0, listOf(startTime, now).distinct())
    }

    override fun onEnd() {
        // Reset so a later timing to the same value does not reuse this timeline.
        startTime = 0.0
    }
}

/** withSpring config, physics or duration based (mutually exclusive). */
data class SpringConfig(
    val stiffness: Double? = null,
    val damping: Double? = null,
    val mass: Double? = null,
    val velocity: Double? = null,
    val overshootClamping: Boolean? = null,
    val energyThreshold: Double? = null,
    val duration: Double? = null,
    val dampingRatio: Double? = null,
    val clampMin: Double? = null,
    val clampMax: Double? = null,
    val reduceMotion: ReduceMotion? = null,
)

/**
 * withSpring (spring.ts + springUtils.ts) as a native `spring()`.
 *
 * Reanimated integrates `m·x'' + c·x' + k·x = 0`; Compose's spring is the same
 * ODE with the mass folded in: `dampingRatio = c / (2·√(k·m))`,
 * `stiffness = k / m`. A duration config gets `k` from Reanimated's own
 * bisection. Completion keeps Reanimated's rule (energy below
 * `energyThreshold` of the initial energy, then snap to the target), checked
 * on the Animatable's value and velocity each frame; the spec's
 * `visibilityThreshold` sits below it so Compose never ends the run first.
 * `overshootClamping` is an Animatable bound: crossing the target ends the run
 * there (`BoundReached`), as Reanimated's `isAnimationTerminatingCalculation`
 * does.
 */
class SpringAnimation(
    private val target: Double,
    user: SpringConfig = SpringConfig(),
    onDone: ((Boolean) -> Unit)? = null,
) : NativeAnimation() {
    override val toValue: Double get() = target

    // DefaultSpringConfig = Gentle + GentleWithDuration, merged with user values.
    internal var stiffness = user.stiffness ?: 900.0
    internal val damping = user.damping ?: 120.0
    internal val mass = user.mass ?: 4.0
    internal val configVelocity = user.velocity ?: 0.0
    internal val overshootClamping = user.overshootClamping ?: false
    internal val energyThreshold = user.energyThreshold ?: 6e-9
    internal var duration = user.duration ?: 550.0
    internal val dampingRatio = user.dampingRatio ?: 1.0
    private val hasClamp = user.clampMin != null || user.clampMax != null
    private val clampMin = user.clampMin
    private val clampMax = user.clampMax
    internal val useDuration = (user.duration != null && user.duration != 0.0) ||
        (user.dampingRatio != null && user.dampingRatio != 0.0)
    internal var skipAnimation = false

    private var started = false
    var startTimestamp = 0.0
    var startValue = 0.0
    var zeta = 0.0
    var omega0 = 0.0
    var initialEnergy = 0.0
    /** Offset-space bounds of an overshootClamping run. */
    private var bounds: ClosedFloatingPointRange<Double>? = null

    override val velocity: Double get() = if (started) runVelocity else configVelocity
    override val lastTimestamp: Double get() = frameTime

    init {
        current = target
        callback = onDone
        skipAnimation = !configIsValid()
        if (duration == 0.0) skipAnimation = true
        reduceMotion = resolveReduceMotion(user.reduceMotion)
    }

    private fun configIsValid(): Boolean {
        var ok = true
        for (v in listOf(stiffness, damping, dampingRatio, mass, energyThreshold)) if (v <= 0) ok = false
        if (duration < 0) ok = false
        if (clampMin != null && clampMax != null && clampMin != 0.0 && clampMax != 0.0 && clampMin > clampMax) ok = false
        return ok
    }

    override fun launch(value: Double, now: Double, previous: Animation?) {
        started = true
        current = value
        val prevSpring = previous as? SpringAnimation
        // isTriggeredTwice: duration/dampingRatio are not fields of the JS
        // animation object, so they always compare equal (undefined === undefined).
        val triggeredTwice = prevSpring != null && prevSpring.lastTimestamp != 0.0 &&
            prevSpring.startTimestamp != 0.0 && prevSpring.toValue == target
        val x0 = if (triggeredTwice) prevSpring!!.startValue else value - target
        startValue = x0

        var v = if (previous != null) {
            val prevVelocity = previous.velocity
            val s = if (triggeredTwice) prevVelocity else prevVelocity + configVelocity
            if (s.isNaN()) 0.0 else s
        } else configVelocity
        // Clip velocity that would drive the first frame away from the new target.
        if ((target > value && v < 0) || (target < value && v > 0)) v = 0.0

        if (triggeredTwice) {
            zeta = prevSpring!!.zeta
            omega0 = prevSpring.omega0
            bounds = prevSpring.bounds
        } else {
            var k = stiffness
            if (useDuration) {
                k = stiffnessForDuration(x0, v)
                stiffness = k
            }
            initialCalculations(k)
            if (hasClamp) zeta = scaleZetaToMatchClamps()
            if (overshootClamping) bounds = min(0.0, x0)..max(0.0, x0)
        }
        initialEnergy = energy(x0, configVelocity, stiffness, mass)
        // The first step runs from the previous spring's / decay's last frame
        // (Reanimated: lastTimestamp = previous.lastTimestamp || now; one
        // step advances at most 64 ms).
        val prevLast = previous?.lastTimestamp ?: 0.0
        val origin = if (prevLast != 0.0) max(prevLast, now - 64) else now
        startTimestamp = if (triggeredTwice) (prevSpring!!.startTimestamp.takeIf { it != 0.0 } ?: now) else now

        // A skipped spring, or one with no initial energy, ends on its first step.
        if (skipAnimation || initialEnergy == 0.0) return endNow(target)

        val threshold = sqrt(2 * energyThreshold * initialEnergy / stiffness) / 2
        val spec = spring(
            // Reanimated takes the critically damped path for any zeta >= 1.
            dampingRatio = min(zeta, 1.0).toFloat(),
            stiffness = (omega0 * omega0).toFloat(),
            visibilityThreshold = threshold.toFloat(),
        )
        run(target, value - target, spec, v, listOf(origin, now).distinct(), bounds)
    }

    override fun terminates(offset: Double, velocity: Double): Boolean =
        energy(offset, velocity, stiffness, mass) / initialEnergy <= energyThreshold

    /** initialCalculations: zeta and the undamped angular frequency. */
    private fun initialCalculations(k: Double) {
        if (skipAnimation) {
            zeta = 0.0; omega0 = 0.0
            return
        }
        omega0 = sqrt(k / mass)
        zeta = if (useDuration) dampingRatio else damping / (2 * sqrt(stiffness * mass))
    }

    private fun scaleZetaToMatchClamps(): Double {
        if (startValue == 0.0) return zeta
        val (first, second) = if (startValue <= 0) clampMin to clampMax else clampMax to clampMin
        val r1 = second?.let { abs((it - target) / startValue) }
        val r2 = first?.let { abs((it - target) / startValue) }
        val z1 = r1?.let { abs(ln(it) / PI) }
        val z2 = r2?.let { abs(ln(it) / (2 * PI)) }
        return listOfNotNull(z1, z2, zeta).max()
    }

    /** calculateNewStiffnessToMatchDuration (bisection, Reanimated's constants). */
    private fun stiffnessForDuration(x0: Double, v0: Double): Double {
        if (skipAnimation) return 0.0
        val zeta = dampingRatio
        val threshold = energyThreshold
        val m = mass
        val targetDuration = duration
        val diff = { stiffness: Double ->
            val settlingDuration = (targetDuration * 1.5) / 1000
            val omega0 = sqrt(stiffness / m) * zeta
            val xtk = (x0 + (v0 + x0 * omega0) * settlingDuration) * exp(-omega0 * settlingDuration)
            val vtk = (x0 + (v0 + x0 * omega0) * settlingDuration) * exp(-omega0 * settlingDuration) * -omega0 +
                (v0 + x0 * omega0) * exp(-omega0 * settlingDuration)
            val e0 = energy(x0, v0, stiffness, m)
            val etk = energy(xtk, vtk, stiffness, m)
            etk / e0 - threshold
        }
        return bisectRoot(Math.ulp(1.0), 8e3, diff, threshold * 1e-3, 100)
    }

    private companion object {
        fun energy(displacement: Double, velocity: Double, stiffness: Double, mass: Double): Double =
            0.5 * stiffness * displacement.pow(2) + 0.5 * mass * velocity.pow(2)

        fun bisectRoot(minIn: Double, maxIn: Double, func: (Double) -> Double, precision: Double, maxIterations: Int): Double {
            var min = minIn
            var max = maxIn
            val direction = if (func(max) >= func(min)) 1 else -1
            var idx = maxIterations
            var current = (max + min) / 2
            while (abs(func(current)) > precision && idx > 0) {
                idx -= 1
                if (func(current) * direction < 0) min = current else max = current
                current = (min + max) / 2
            }
            return current
        }
    }
}

/**
 * withDecay (decay.ts, rigidDecay.ts, rubberBandDecay.ts; native
 * VELOCITY_EPS), ported and stepped per frame: Compose's `exponentialDecay`
 * uses another formula and has no rubber band.
 */
class DecayAnimation(
    private val initial: Double = 0.0,
    private val deceleration: Double = 0.998,
    private val velocityFactor: Double = 1.0,
    private val clamp: Pair<Double, Double>? = null,
    private val rubberBandEffect: Boolean = false,
    private val rubberBandFactor: Double = 0.6,
    reduce: ReduceMotion? = null,
    onDone: ((Boolean) -> Unit)? = null,
) : Animation() {
    override var velocity = initial
    var initialVelocity = 0.0
    override var lastTimestamp = 0.0
    var startTimestamp = 0.0
    private var springActive = false
    private var detached = false
    private var ended = false

    override val running: Boolean get() = !ended && !detached

    init {
        callback = onDone
        reduceMotion = resolveReduceMotion(reduce)
        require(velocityFactor > 0)
        require(!rubberBandEffect || clamp != null) { "You need to set `clamp` property when using `rubberBandEffect`." }
    }

    private fun baseOnStart(value: Double, now: Double) {
        current = value
        lastTimestamp = now
        startTimestamp = now
        initialVelocity = initial
        velocity = initial
        if (reduceMotion == true && clamp != null) {
            if (value < clamp.first) current = clamp.first
            else if (value > clamp.second) current = clamp.second
        }
    }

    override fun reducedStart(value: Double, now: Double): Double {
        baseOnStart(value, now)
        ended = true
        return current
    }

    override fun launch(value: Double, now: Double, previous: Animation?) {
        baseOnStart(value, now)
        step(now)
    }

    private fun step(now: Double) {
        if (detached) return
        val finished = onFrame(now)
        sink.write(current)
        if (finished) {
            ended = true
            sink.done()
        } else {
            AnimationEngine.requestAnimationFrame(::step)
        }
    }

    override fun detach() {
        detached = true
    }

    internal fun onFrame(now: Double): Boolean =
        if (rubberBandEffect && clamp != null) rubberBand(now) else rigid(now)

    private fun rigid(now: Double): Boolean {
        val deltaTime = min(max(now - lastTimestamp, 0.0), 64.0)
        val v = velocity * exp(-(1 - deceleration) * (now - startTimestamp) * SLOPE_FACTOR)
        current += (v * velocityFactor * deltaTime) / 1000
        velocity = v
        lastTimestamp = now
        if (clamp != null) {
            if (initialVelocity < 0 && current <= clamp.first) {
                current = clamp.first
                return true
            } else if (initialVelocity > 0 && current >= clamp.second) {
                current = clamp.second
                return true
            }
        }
        return abs(v) < VELOCITY_EPS
    }

    private fun rubberBand(now: Double): Boolean {
        val c = clamp!!
        val deltaTime = min(max(now - lastTimestamp, 0.0), 64.0)
        val clampValue = if (abs(current - c.first) < abs(current - c.second)) c.first else c.second
        var derivative = 0.0
        if (current < c.first || current > c.second) derivative = current - clampValue
        val v = velocity * exp(-(1 - deceleration) * (now - startTimestamp) * SLOPE_FACTOR) -
            derivative * rubberBandFactor
        if (abs(derivative) > DERIVATIVE_EPS) {
            springActive = true
        } else if (springActive) {
            current = clampValue
            return true
        } else if (abs(v) < VELOCITY_EPS) {
            return true
        }
        current += (v * velocityFactor * deltaTime) / 1000
        velocity = v
        lastTimestamp = now
        return false
    }

    companion object {
        const val VELOCITY_EPS = 1.0
        const val SLOPE_FACTOR = 0.1
        const val DERIVATIVE_EPS = 0.1
    }
}

/**
 * withDelay: waits on the engine's frames (`now - startTime >= delay`), the
 * previous animation running meanwhile, then starts `next` from the value
 * shown, handing it that previous animation.
 */
class DelayAnimation(
    private val delayMs: Double,
    private val next: Animation,
    reduce: ReduceMotion? = null,
) : Animation() {
    override val isHigherOrder = true
    private var startTime = 0.0
    private var started = false
    private var detached = false
    private var previousAnimation: Animation? = null

    override val running: Boolean get() = !detached && (!started || next.running)

    init {
        current = next.current
        reduceMotion = resolveReduceMotion(reduce)
        callback = { finished -> next.callback?.invoke(finished) }
    }

    override fun adopts(previous: Animation) = !started && previous === previousAnimation

    override fun launch(value: Double, now: Double, previous: Animation?) {
        startTime = now
        started = false
        current = value
        previousAnimation = if (previous === this) previousAnimation else previous
        if (next.reduceMotion == null) next.reduceMotion = reduceMotion
        step(now)
    }

    private fun step(now: Double) {
        if (detached) return
        if (now - startTime >= delayMs || reduceMotion == true) {
            val previous = previousAnimation?.takeIf { it.running }
            val waited = previousAnimation
            previousAnimation = null
            started = true
            next.start(sink.read(), now, previous, sink)
            if (waited != null && !next.adopts(waited)) waited.detach()
        } else {
            AnimationEngine.requestAnimationFrame(::step)
        }
    }

    override fun detach() {
        if (detached) return
        detached = true
        previousAnimation?.detach()
        previousAnimation = null
        if (started) next.detach()
    }
}

// Builders with Reanimated's names.
fun withTiming(
    to: Double,
    duration: Double = 300.0,
    easing: Easing = InOutQuad,
    reduceMotion: ReduceMotion? = null,
    callback: ((Boolean) -> Unit)? = null,
) = TimingAnimation(to, duration, easing, reduceMotion, callback)

fun withSpring(to: Double, config: SpringConfig = SpringConfig(), callback: ((Boolean) -> Unit)? = null) =
    SpringAnimation(to, config, callback)

fun withDecay(
    velocity: Double,
    deceleration: Double = 0.998,
    clamp: Pair<Double, Double>? = null,
    rubberBandEffect: Boolean = false,
    rubberBandFactor: Double = 0.6,
    callback: ((Boolean) -> Unit)? = null,
) = DecayAnimation(velocity, deceleration, 1.0, clamp, rubberBandEffect, rubberBandFactor, null, callback)

fun withDelay(delayMs: Double, next: Animation, reduceMotion: ReduceMotion? = null) =
    DelayAnimation(delayMs, next, reduceMotion)
