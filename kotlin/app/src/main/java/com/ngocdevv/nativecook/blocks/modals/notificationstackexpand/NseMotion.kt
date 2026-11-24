package com.ngocdevv.nativecook.blocks.modals.notificationstackexpand

import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

// Every number the stack uses, ported from React Native's motion.ts.

/** Seconds. One autoPlay loop; rest at both ends (0–1.0 and 7.85–9, 24 %). */
const val LOOP = 9f

/** autoPlay: 2 s after the last touch it eases to rest and restarts at t = 0. */
internal const val RESUME_AFTER = 2f

/** The loop's events, in seconds. */
internal object NseT {
    const val ghostIn = 0.55f
    const val tapOpen = 1.0f
    const val expand = 1.05f
    const val swipeStart = 2.55f
    const val swipeEnd = 3.15f
    const val flingSpan = 0.24f
    const val gap = 3.3f
    const val tapLess = 4.55f
    const val collapse = 4.6f
    const val peekStart = 5.75f
    const val peekRelease = 6.45f
    const val arrive = 7.25f
    const val arriveSpan = 0.5f
    const val rest = 7.85f
}

/** Card geometry, dp. */
internal const val CARD_H = 84f
internal const val GAP = 8f
internal const val PEEK = 9f
internal const val DEPTH_SCALE = 0.05f
internal const val MAX_DEPTH = 2f
internal const val HEADER_H = 44f
internal const val RADIUS = 20f
internal const val MAX_CARDS = 4

internal class NseSpring(val response: Float, val ratio: Float) {
    val stiffness: Float get() = (2f * PI.toFloat() / response).pow(2)
    val damping: Float get() = 2f * ratio * sqrt(stiffness)
}

internal val EXPAND_SPRING = NseSpring(0.5f, 0.74f)
internal val COLLAPSE_SPRING = NseSpring(0.42f, 0.86f)
internal const val STAGGER = 0.05f
internal val GAP_SPRING = NseSpring(0.42f, 0.8f)
internal val PUSH_SPRING = NseSpring(0.45f, 0.8f)
internal val SWIPE_SPRING = NseSpring(0.38f, 0.82f)
internal val PEEK_SPRING = NseSpring(0.38f, 0.62f)

/** Swipe to clear. */
internal const val SWIPE_DRAG = 150f
internal const val CLEAR_W = 84f
internal const val FLING_FRACTION = 0.42f
internal const val FLING_VELOCITY = 900f
internal const val OPEN_FRACTION = 0.6f
internal const val RUBBER_RIGHT = 0.3f

/** Drag down on the collapsed stack. */
internal const val PEEK_MAX = 0.32f
internal const val PEEK_RANGE = 150f
internal const val PEEK_OPEN_DISTANCE = 110f
internal const val PEEK_OPEN_VELOCITY = 700f
internal const val GHOST_PEEK = 64f

/** The cleared message's return: drops 22 dp onto the top of the stack. */
internal const val ARRIVE_DROP = 22f
internal const val ARRIVE_SCALE = 0.03f

internal fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
internal fun mix(u: Float, a: Float, b: Float) = a + (b - a) * u
internal fun seg(t: Float, start: Float, span: Float) = clamp01((t - start) / span)
internal fun easeOutCubic(x: Float): Float { val u = 1f - x; return 1f - u * u * u }
internal fun easeInOutCubic(x: Float): Float =
    if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
/** Reanimated's default withTiming curve. */
internal fun easeInOutQuad(x: Float): Float = if (x < 0.5f) 2f * x * x else 1f - (-2f * x + 2f).pow(2) / 2f

/** The closed-form step response of the springs above, so autoPlay is a pure function of t. */
internal fun springAt(t: Float, s: NseSpring): Float {
    if (t <= 0f) return 0f
    val w = 2f * PI.toFloat() / s.response
    val wd = w * sqrt(1f - s.ratio * s.ratio)
    return 1f - exp(-s.ratio * w * t) * (cos(wd * t) + (s.ratio * w / wd) * sin(wd * t))
}

/** Collapsed: the top of a card `depth` places back (scaled about its centre so its bottom edge peeks). */
internal fun collapsedY(depth: Float): Float {
    val d = depth.coerceIn(0f, MAX_DEPTH)
    val k = 1f - DEPTH_SCALE * d
    return CARD_H + d * PEEK - CARD_H * (1f + k) / 2f
}

/** Expanded: the top of the card in list slot `slot`, under the header. */
internal fun expandedY(slot: Float) = HEADER_H + slot * (CARD_H + GAP)

/** How tall the group stands for `count` cards (fractional while one leaves) at fan-out `e`. */
internal fun groupHeight(e: Float, count: Float): Float {
    if (count <= 0f) return 0f
    val n = count
    val closed = if (n < 1f) n * CARD_H else CARD_H + min(n - 1f, MAX_DEPTH) * PEEK
    val open = if (n < 1f) n * (HEADER_H + CARD_H) else HEADER_H + n * CARD_H + (n - 1f) * GAP
    return mix(e, closed, open)
}

/** Pulling down on the stack: most of PEEK_MAX within PEEK_RANGE, never past it. */
internal fun peekFromDrag(dy: Float): Float = if (dy <= 0f) 0f else PEEK_MAX * (1f - exp(-dy / PEEK_RANGE))

/** Past the right edge the card follows the finger less and less. */
internal fun rubberRight(x: Float): Float = if (x <= 0f) x else x * RUBBER_RIGHT / (1f + x / 220f)

/** Rounds like React Native's `put` (to 1e-4) so a held pose writes nothing. */
internal fun quantize(v: Float) = (round(v * 10000.0) / 10000.0).toFloat()

/**
 * A float that a script sets directly or that runs Reanimated-style animations (spring with velocity,
 * timing, both after a delay). Backed by snapshot state, read only in layer and draw lambdas.
 * Stepped by the screen's single frame loop.
 */
internal class NseValue(initial: Float, private val eps: Float = 0.0005f) {
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

    fun spring(to: Float, s: NseSpring, v0: Float = 0f, delaySeconds: Float = 0f) {
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
