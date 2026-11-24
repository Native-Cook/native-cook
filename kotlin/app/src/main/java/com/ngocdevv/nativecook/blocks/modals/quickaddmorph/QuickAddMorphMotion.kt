package com.ngocdevv.nativecook.blocks.modals.quickaddmorph

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

// Every number the morph uses, ported from the React Native block's motion.ts.

/** Seconds. One autoPlay loop; rest at both ends (0–0.62 and 8.05–9, 17 %). */
const val LOOP = 9f

/** autoPlay: 2 s after the last touch it eases to rest and restarts at t = 0. */
internal const val RESUME_AFTER = 2f

/** The loop's events, in seconds. */
internal object QuickAddT {
    const val GHOST_IN = 0.62f
    const val OPEN = 1.0f // finger lifts on +
    const val TYPE_START = 1.9f
    const val TYPE_STEP = 0.07f // per character
    const val PRIORITY = 3.3f
    const val LIST = 3.8f
    const val ADD = 4.4f
    const val FLY = 4.78f
    const val FLY_SPAN = 0.62f
    const val CHECK = 7.0f
    const val CHECK_SPAN = 0.3f
    const val REMOVE = 7.55f
    const val REMOVE_SPAN = 0.5f
    const val REST = 8.05f
}

/** A spring given as response (s) and damping ratio, mass 1, as in motion.ts `springConfig`. */
internal class QuickAddSpring(val response: Float, val ratio: Float) {
    val stiffness = ((2.0 * PI / response) * (2.0 * PI / response)).toFloat()
    val damping = 2f * ratio * sqrt(stiffness)
}

/** Two staggered springs: width leads, height follows 70 ms later. */
internal val WIDTH_SPRING = QuickAddSpring(0.46f, 0.74f)
internal val HEIGHT_SPRING = QuickAddSpring(0.52f, 0.78f)
internal const val HEIGHT_DELAY = 0.07f
internal val COLLAPSE_SPRING = QuickAddSpring(0.36f, 0.9f)
internal val PART_SPRING = QuickAddSpring(0.42f, 0.76f)
internal val CHIP_SPRING = QuickAddSpring(0.3f, 0.8f)
internal val DONE_SPRING = QuickAddSpring(0.34f, 0.82f)

/** Rows start to part this long after the pill takes off. */
internal const val PART_DELAY = 0.16f
/** The row fades in under the pill over the last part of the flight. */
internal const val LAND_DELAY = 0.46f
internal const val LAND_SPAN = 0.16f
internal const val FAB_DELAY = 0.3f
internal const val FAB_SPAN = 0.36f
internal const val CONTENT_DELAY = 0.24f
internal const val CONTENT_IN = 0.42f
internal const val CONTENT_OUT = 0.14f
/** Field, chips and Add rise in on these shares of the content clock. */
internal val CONTENT_STAGGER = floatArrayOf(0f, 0.14f, 0.28f)
internal const val CONTENT_ITEM = 0.72f
internal const val CONTENT_RISE = 10f

internal const val PLUS_TURN = 135f // degrees
internal const val FLY_ARC = 22f // dp, sideways bow of the flight
internal const val DRAG_CLOSE_DISTANCE = 90f
internal const val DRAG_CLOSE_VELOCITY = 700f
internal const val DRAG_UP_RUBBER = 0.15f

/** Corner radii along the morph. */
internal const val RADIUS_FAB = 30f
internal const val RADIUS_CARD = 26f
internal const val RADIUS_PILL = 22f
internal const val RADIUS_ROW = 16f

/** Reduced motion: every change is a 180 ms fade instead of a spring. */
internal const val REDUCED_SECONDS = 0.18f

internal fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

internal fun mix(u: Float, a: Float, b: Float) = a + (b - a) * u

/** 0 before `start`, 1 after `start + span` (motion.ts `seg`). */
internal fun seg(t: Float, start: Float, span: Float) = clamp01((t - start) / span)

internal fun easeOutCubic(x: Float): Float {
    val u = 1f - x
    return 1f - u * u * u
}

internal fun easeInOutCubic(x: Float): Float =
    if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

/** Reanimated's default `withTiming` curve, `Easing.inOut(Easing.quad)`. */
internal fun easeInOutQuad(x: Float): Float =
    if (x < 0.5f) 2f * x * x else 1f - (-2f * x + 2f).pow(2) / 2f

/** The closed-form step response of the springs above, so autoPlay is a pure function of t. */
internal fun springAt(t: Float, response: Float, ratio: Float): Float {
    if (t <= 0f) return 0f
    val w = 2.0 * PI / response
    val wd = w * sqrt(1.0 - ratio * ratio)
    return (1.0 - exp(-ratio * w * t) * (cos(wd * t) + (ratio * w / wd) * sin(wd * t))).toFloat()
}

internal fun springAt(t: Float, spring: QuickAddSpring) = springAt(t, spring.response, spring.ratio)

/** Reanimated's `interpolateColor` in RGB: channels mixed in gamma 2.2 space, clamped. */
internal fun mixColor(u: Float, a: Color, b: Color): Color {
    val k = clamp01(u)
    fun channel(x: Float, y: Float) = (x.pow(2.2f) * (1f - k) + y.pow(2.2f) * k).pow(1f / 2.2f)
    return Color(channel(a.red, b.red), channel(a.green, b.green), channel(a.blue, b.blue), a.alpha + (b.alpha - a.alpha) * k)
}

/** Shared by every animated value of the screen: one invalidation per frame when anything moved. */
internal class QuickAddHost {
    val tick = mutableLongStateOf(0L)
    var dirty = false
    val values = ArrayList<QuickAddValue>()

    fun step(dt: Float) {
        for (i in values.indices) values[i].step(dt)
    }

    fun publish() {
        if (dirty) {
            dirty = false
            tick.longValue += 1
        }
    }
}

/**
 * One animated number, Reanimated's shared value: `set` jumps (and cancels), `spring` and `timing` animate
 * after an optional delay, and `onDone` runs only when an animation finishes (not when it is replaced).
 * A spring keeps the velocity of whatever moved the value before, like `withSpring`.
 */
internal class QuickAddValue(private val host: QuickAddHost, initial: Float) {
    var value = initial
        private set
    var velocity = 0f
        private set
    private var kind = IDLE
    private var target = initial
    private var delay = 0f
    private var started = false
    private var stiffness = 0f
    private var damping = 0f
    private var clampOvershoot = false
    private var startSign = 0f
    private var startVelocity = Float.NaN
    private var from = 0f
    private var duration = 1f
    private var elapsed = 0f
    private var easing: (Float) -> Float = ::easeInOutQuad
    private var done: (() -> Unit)? = null

    init {
        host.values.add(this)
    }

    val running get() = kind != IDLE

    fun set(next: Float) {
        kind = IDLE
        done = null
        velocity = 0f
        if (value != next) {
            value = next
            host.dirty = true
        }
    }

    fun spring(
        to: Float,
        spec: QuickAddSpring,
        delay: Float = 0f,
        velocity: Float = Float.NaN,
        overshootClamping: Boolean = false,
        onDone: (() -> Unit)? = null,
    ) {
        kind = SPRING
        target = to
        this.delay = delay
        started = false
        stiffness = spec.stiffness
        damping = spec.damping
        clampOvershoot = overshootClamping
        startVelocity = velocity
        done = onDone
        host.dirty = true
    }

    fun timing(to: Float, seconds: Float, delay: Float = 0f, curve: (Float) -> Float = ::easeInOutQuad, onDone: (() -> Unit)? = null) {
        kind = TIMING
        target = to
        this.delay = delay
        started = false
        duration = max(seconds, 0.0001f)
        elapsed = 0f
        easing = curve
        done = onDone
        host.dirty = true
    }

    /** A spring, or a 180 ms fade under reduced motion (the RN `to` helper). */
    fun to(target: Float, spec: QuickAddSpring, reduced: Boolean, delay: Float = 0f, onDone: (() -> Unit)? = null) {
        if (reduced) timing(target, REDUCED_SECONDS, delay, onDone = onDone) else spring(target, spec, delay, onDone = onDone)
    }

    fun step(dt: Float) {
        if (kind == IDLE) return
        var left = dt
        if (delay > 0f) {
            if (delay >= left) {
                delay -= left
                return
            }
            left -= delay
            delay = 0f
        }
        if (!started) {
            started = true
            if (kind == TIMING) {
                from = value
                elapsed = 0f
                velocity = 0f
            } else {
                if (!startVelocity.isNaN()) velocity = startVelocity
                startSign = sign(value - target)
            }
        }
        val before = value
        if (kind == TIMING) {
            elapsed += left
            val u = clamp01(elapsed / duration)
            value = from + (target - from) * easing(u)
            if (u >= 1f) finish()
        } else {
            advanceSpring(left)
            val offset = value - target
            if (clampOvershoot && startSign != 0f && sign(offset) == -startSign) {
                finish()
            } else if (abs(offset) < REST_DISTANCE && abs(velocity) < REST_SPEED) {
                finish()
            }
        }
        if (value != before) host.dirty = true
    }

    /** The damped oscillator in closed form over `dt`, offset from the target. */
    private fun advanceSpring(dt: Float) {
        val x0 = (value - target).toDouble()
        val v0 = velocity.toDouble()
        val k = stiffness.toDouble()
        val w0 = sqrt(k)
        val zeta = damping / (2.0 * w0)
        val t = dt.toDouble()
        val x: Double
        val v: Double
        if (zeta < 0.999) {
            val wd = w0 * sqrt(1.0 - zeta * zeta)
            val e = exp(-zeta * w0 * t)
            val c = cos(wd * t)
            val s = sin(wd * t)
            x = e * (x0 * c + (v0 + zeta * w0 * x0) / wd * s)
            v = e * (v0 * c - (zeta * w0 * v0 + w0 * w0 * x0) / wd * s)
        } else if (zeta <= 1.001) {
            val b = v0 + w0 * x0
            val e = exp(-w0 * t)
            x = (x0 + b * t) * e
            v = (b - w0 * (x0 + b * t)) * e
        } else {
            val root = sqrt(zeta * zeta - 1.0)
            val r1 = -w0 * (zeta - root)
            val r2 = -w0 * (zeta + root)
            val c1 = (v0 - r2 * x0) / (r1 - r2)
            val c2 = x0 - c1
            x = c1 * exp(r1 * t) + c2 * exp(r2 * t)
            v = c1 * r1 * exp(r1 * t) + c2 * r2 * exp(r2 * t)
        }
        value = (target + x).toFloat()
        velocity = v.toFloat()
    }

    private fun finish() {
        value = target
        velocity = 0f
        kind = IDLE
        val callback = done
        done = null
        host.dirty = true
        callback?.invoke()
    }

    private companion object {
        const val IDLE = 0
        const val SPRING = 1
        const val TIMING = 2
        const val REST_DISTANCE = 0.001f
        const val REST_SPEED = 0.01f
    }
}
