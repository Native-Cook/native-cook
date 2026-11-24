package com.ngocdevv.nativecook.blocks.modals.coachmarkspotlight

import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

// Every number the tour uses, ported from React Native's motion.ts.

/** Seconds. One autoPlay loop; rest at both ends on step 1 (0–1.05 and 8.35–9, 19 %). */
const val LOOP = 9f

/** autoPlay: 2 s after the last touch it eases to rest and restarts at t = 0. */
internal const val RESUME_AFTER = 2f

/** The loop's events, in seconds. */
internal object CmsT {
    const val ghostIn = 0.5f
    const val next1 = 1.05f
    const val morph1 = 1.15f
    const val tapCard = 3.25f
    const val morph2 = 3.35f
    const val dragStart = 5.0f
    const val dragEnd = 5.45f
    const val morph3 = 5.5f
    const val skip = 6.8f
    const val fadeOut = 6.85f
    const val fadeOutSpan = 0.4f
    const val reset = 7.3f
    const val intro = 7.45f
    const val introSpan = 0.85f
    const val tipIn = 7.95f
    const val tipInSpan = 0.4f
    const val rest = 8.35f
}

internal class CmsSpring(val response: Float, val ratio: Float) {
    val stiffness: Float get() = (2f * PI.toFloat() / response).pow(2)
    val damping: Float get() = 2f * ratio * sqrt(stiffness)
}

/** A step change: the cutout travels on this curve; the tooltip follows 70 ms later. */
internal const val MORPH = 0.75f
internal const val TIP_LAG = 0.07f
/** While the cutout travels it narrows a little about its centre, then opens onto the target. */
internal const val TRAVEL_SQUEEZE = 0.3f
/** The finger: the same morph on springs. */
internal val MORPH_SPRING = CmsSpring(0.55f, 0.82f)
internal val TIP_SPRING = CmsSpring(0.6f, 0.78f)
internal val DRAG_SPRING = CmsSpring(0.4f, 0.7f)
/** The opening iris: from a circle IRIS_SCALE times the first target down onto it, landing 2 % tight. */
internal val IRIS_SPRING = CmsSpring(0.6f, 0.8f)
internal const val IRIS_SCALE = 3.2f

/** Cutout padding around each target, dp, and the ring drawn on its edge. */
internal const val PAD = 8f
internal const val SOFT_EDGE = 5f
internal const val RING_INSET = 3f
/** A ring breathes out of the cutout while a step holds: period, travel, starting opacity. */
internal const val PULSE_PERIOD = 1.4f
internal const val PULSE_GROW = 14f
internal const val PULSE_ALPHA = 0.5f
/** autoPlay holds in which the ring breathes: [start, end] in s (the first wraps through the loop's end). */
internal val HOLDS = arrayOf(floatArrayOf(8.35f, 10.0f), floatArrayOf(1.95f, 3.2f), floatArrayOf(4.15f, 4.95f), floatArrayOf(6.3f, 6.78f))

/** Tooltip, dp. */
internal const val TIP_MAX_W = 340f
internal const val TIP_H = 176f
internal const val TIP_GAP = 14f
internal const val ARROW_W = 20f
internal const val ARROW_H = 9f
internal const val TIP_FADE_SCALE = 0.88f
/** The tooltip is opaque except in the last third of its fade (no see-through card). */
internal const val TIP_OPACITY_GAIN = 3f
/** Swipe the tooltip: past this (or this fast) it goes back (right) or on (left). */
internal const val SWIPE_STEP = 64f
internal const val SWIPE_VELOCITY = 500f
internal const val SWIPE_RUBBER = 0.45f
internal const val GHOST_DRAG = 78f

/** rgba(16,21,28,0.66) */
internal const val DIM_ALPHA = 0.66f

/** A rounded box in screen dp; r is the corner radius. */
data class CmsBox(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float)

internal fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
internal fun mix(u: Float, a: Float, b: Float) = a + (b - a) * u
internal fun seg(t: Float, start: Float, span: Float) = clamp01((t - start) / span)
internal fun easeOutCubic(x: Float): Float { val u = 1f - x; return 1f - u * u * u }
internal fun easeInCubic(x: Float): Float = x * x * x
internal fun easeInOutCubic(x: Float): Float =
    if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
/** Reanimated's default withTiming curve. */
internal fun easeInOutQuad(x: Float): Float = if (x < 0.5f) 2f * x * x else 1f - (-2f * x + 2f).pow(2) / 2f

/** The closed-form step response of a spring, so autoPlay is a pure function of t. */
internal fun springAt(t: Float, s: CmsSpring): Float {
    if (t <= 0f) return 0f
    val w = 2f * PI.toFloat() / s.response
    val wd = w * sqrt(1f - s.ratio * s.ratio)
    return 1f - exp(-s.ratio * w * t) * (cos(wd * t) + (s.ratio * w / wd) * sin(wd * t))
}

/** Piecewise-linear value of a per-step list at a fractional step p (clamped to the ends). */
internal fun along(values: FloatArray, p: Float): Float {
    val last = values.size - 1
    if (p <= 0f) return values[0]
    if (p >= last) return values[last]
    val i = floor(p).toInt()
    return mix(p - i, values[i], values[i + 1])
}

/**
 * The cutout at progress p (0 = first target, 1 = second, …): position, size and corner radius
 * interpolated between neighbouring targets, narrowed mid-travel, then opened by `iris` (1 = a circle
 * IRIS_SCALE times the first target's size around it, 0 = closed onto it).
 */
internal fun cutoutAt(targets: List<CmsBox>, p: Float, iris: Float): CmsBox {
    val last = targets.size - 1
    val q = p.coerceIn(0f, last.toFloat())
    val i = if (last == 0) 0 else if (q >= last) last - 1 else floor(q).toInt()
    val u = if (last == 0) 0f else q - i
    val a = targets[i]
    val b = targets[min(i + 1, last)]
    val squeeze = 1f - TRAVEL_SQUEEZE * sin(PI.toFloat() * u)
    val cx = mix(u, a.x + a.w / 2f, b.x + b.w / 2f)
    val cy = mix(u, a.y + a.h / 2f, b.y + b.h / 2f)
    var w = mix(u, a.w, b.w) * squeeze
    var h = mix(u, a.h, b.h) * squeeze
    var r = min(mix(u, a.r, b.r) * squeeze, min(w, h) / 2f)
    var x = cx - w / 2f
    var y = cy - h / 2f
    if (iris > 0f) {
        val f = targets[0]
        val fx = f.x + f.w / 2f
        val fy = f.y + f.h / 2f
        val big = max(f.w, f.h) * IRIS_SCALE
        w = mix(iris, w, big)
        h = mix(iris, h, big)
        r = mix(iris, r, big / 2f)
        x = mix(iris, x, fx - big / 2f)
        y = mix(iris, y, fy - big / 2f)
    }
    return CmsBox(x, y, w, h, r)
}

/** Rounds like React Native's `put` (to 1e-4) so a held pose writes nothing. */
internal fun quantize(v: Float) = (round(v * 10000.0) / 10000.0).toFloat()

/**
 * A float that a script sets directly or that runs Reanimated-style animations (spring with velocity,
 * timing, both after a delay). Backed by snapshot state, read only in layer and draw lambdas.
 * Stepped by the screen's single frame loop.
 */
internal class CmsValue(initial: Float, private val eps: Float = 0.0005f) {
    private val state = mutableFloatStateOf(initial)
    private var kind = 0 // 0 none, 1 spring, 2 timing
    private var target = 0f
    private var from = 0f
    private var velocity = 0f
    private var delay = 0f
    private var elapsed = 0f
    private var duration = 0f
    private var stiffness = 0f
    private var damping = 0f
    private var started = false

    /** Called whenever an animation starts, so an idle frame loop wakes. */
    var onStart: (() -> Unit)? = null

    val active: Boolean get() = kind != 0

    fun get(): Float = state.floatValue

    /** Sets at once and stops any animation. */
    fun set(v: Float) {
        kind = 0
        velocity = 0f
        if (state.floatValue != v) state.floatValue = v
    }

    /** A script's write: only real changes (to 1e-4). */
    fun put(v: Float) = set(quantize(v))

    fun spring(to: Float, s: CmsSpring, v0: Float = 0f, delaySeconds: Float = 0f) {
        kind = 1
        target = to
        stiffness = s.stiffness
        damping = s.damping
        velocity = v0
        delay = delaySeconds
        elapsed = 0f
        started = false
        onStart?.invoke()
    }

    fun timing(to: Float, seconds: Float, delaySeconds: Float = 0f, easing: (Float) -> Float = ::easeInOutQuad) {
        kind = 2
        target = to
        duration = seconds
        delay = delaySeconds
        elapsed = 0f
        started = false
        timingEasing = easing
        onStart?.invoke()
    }

    private var timingEasing: (Float) -> Float = ::easeInOutQuad

    /** Advances the running animation by dt seconds. */
    fun step(dt: Float) {
        if (kind == 0) return
        var left = dt
        if (delay > 0f) {
            val d = min(delay, left)
            delay -= d
            left -= d
            if (delay > 0f) return
        }
        if (!started) {
            started = true
            from = state.floatValue
        }
        if (kind == 2) {
            elapsed += left
            if (duration <= 0f || elapsed >= duration) {
                kind = 0
                velocity = 0f
                state.floatValue = target
                return
            }
            state.floatValue = mix(timingEasing(elapsed / duration), from, target)
            return
        }
        // Spring, mass 1: semi-implicit Euler in short substeps.
        var x = state.floatValue
        var v = velocity
        var rem = left
        while (rem > 0f) {
            val h = min(rem, 1f / 480f)
            val a = -stiffness * (x - target) - damping * v
            v += a * h
            x += v * h
            rem -= h
        }
        val scale = max(1f, abs(target))
        if (abs(x - target) < eps * scale && abs(v) < eps * 20f * scale) {
            kind = 0
            velocity = 0f
            state.floatValue = target
            return
        }
        velocity = v
        state.floatValue = x
    }
}
