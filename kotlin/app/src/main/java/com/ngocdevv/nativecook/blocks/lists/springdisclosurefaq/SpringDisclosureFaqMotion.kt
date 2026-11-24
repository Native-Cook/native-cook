package com.ngocdevv.nativecook.blocks.lists.springdisclosurefaq

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

// Spring Disclosure FAQ: the film and the spring, as pure functions (a port of faqMotion.ts).
//
// Every row has one number, `open` (0 closed, 1 open, a little past either while the spring
// overshoots). Everything on screen is drawn from the six `open` values sampled at a time:
//   - a row's answer is revealed by the rows below sliding down by H * open,
//   - each row below samples the same value FOLLOW_LAG later than the row above it,
//   - line k of an answer samples it LINE_STAGGER * k later,
//   - the "+" turns into a "−" with it.
// The film is a sum of spring steps at fixed times, so it is a function of the loop time alone and
// the frame at LOOP equals the frame at 0. A finger uses the same spring from wherever a row is.

/** The film's period, seconds. */
const val LOOP = 9.0

internal const val ROW_COUNT = 6
internal const val GROUP_SIZE = 3 // two grouped cards of three rows
internal const val REST_ROW = 0 // the row that is open at rest

// The spring (response 0.46 s, damping ratio 0.72); about 4 % overshoot.
private const val SPRING_RESPONSE = 0.46
private const val SPRING_DAMPING = 0.72
private val OMEGA = 2 * PI / SPRING_RESPONSE
private val OMEGA_D = OMEGA * sqrt(1 - SPRING_DAMPING * SPRING_DAMPING)
private val DECAY = SPRING_DAMPING * OMEGA

internal const val FOLLOW_LAG = 0.03 // s, each row below starts this much after the one above it
internal const val LINE_STAGGER = 0.035 // s between the lines of an answer
private const val LINE_FROM = 0.15 // an answer line fades in while its sample of `open` goes
private const val LINE_TO = 0.85 //   from LINE_FROM to LINE_TO
internal const val LINE_RISE = 10f // dp a line travels up
internal const val LINE_BLUR = 6f // dp of blur (sigma) a line starts from
private const val REDUCED_FADE = 0.18 // s, the plain linear change under reduced motion

// The film: at EVENT_T[k] the ghost finger taps EVENT_ROW[k]; that row opens and EVENT_PREV[k] closes.
internal val EVENT_T = doubleArrayOf(1.1, 3.7, 6.3)
internal val EVENT_ROW = intArrayOf(2, 4, 0)
private val EVENT_PREV = intArrayOf(0, 2, 4)

// The ghost finger around an event at time e.
private const val TOUCH_IN = 0.3 // s before e the dot starts to appear
private const val TOUCH_DOWN = 0.08 // s before e it presses
private const val TOUCH_UP = 0.12 // s after e it lifts
private const val TOUCH_OUT = 0.4 // s after e it is gone

internal const val MODE_AUTO = 0 // the film; `tau` is the loop time
internal const val MODE_MANUAL = 1 // a finger; `tau` is the frame clock in seconds
internal const val MODE_RETURN = 2 // easing back to rest before the film restarts

internal fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x

/** 0 before a, 1 after b. */
internal fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))

internal fun smooth(x: Double): Double {
    val c = clamp01(x)
    return c * c * (3 - 2 * c)
}

/**
 * An underdamped spring from `from` towards `to`, `s` seconds after it was released with velocity
 * `v0` (units per second). Before the release (s <= 0) it is still at `from`.
 */
internal fun springTo(from: Double, to: Double, v0: Double, s: Double): Double {
    if (s <= 0) return from
    val a = from - to
    val b = (v0 + DECAY * a) / OMEGA_D
    return to + exp(-DECAY * s) * (a * cos(OMEGA_D * s) + b * sin(OMEGA_D * s))
}

/** `open` of row j at loop time t (t may be negative for a lagged sample: the rest state). */
internal fun filmOpen(j: Int, t: Double): Double {
    var v = if (j == REST_ROW) 1.0 else 0.0
    for (k in EVENT_T.indices) {
        val s = t - EVENT_T[k]
        if (s <= 0) continue
        val step = springTo(0.0, 1.0, 0.0, s)
        if (EVENT_ROW[k] == j) v += step
        else if (EVENT_PREV[k] == j) v -= step
    }
    return v
}

/** Where row j is heading at loop time t: 1 when it is the open row, else 0. */
internal fun filmTarget(j: Int, t: Double): Double {
    var row = REST_ROW
    for (k in EVENT_T.indices) if (t >= EVENT_T[k]) row = EVENT_ROW[k]
    return if (row == j) 1.0 else 0.0
}

/** Opacity of the ghost finger's dot for event k at loop time t. */
internal fun touchAlpha(k: Int, t: Double): Double {
    val e = EVENT_T[k]
    return smooth(seg(t, e - TOUCH_IN, e - TOUCH_DOWN)) * (1 - smooth(seg(t, e + TOUCH_UP, e + TOUCH_OUT)))
}

/** 0..1, how far the ghost finger is pressed for event k at loop time t. */
internal fun touchPress(k: Int, t: Double): Double {
    val e = EVENT_T[k]
    return smooth(seg(t, e - TOUCH_DOWN, e)) * (1 - smooth(seg(t, e + TOUCH_UP, e + TOUCH_UP + 0.16)))
}

/** How visible an answer line is, from its (lagged) sample of the row's `open`. */
internal fun lineReveal(open: Double) = smooth((open - LINE_FROM) / (LINE_TO - LINE_FROM))

// ---------------------------------------------------------------- a finger's transitions

/** The last transition of every row: from -> to, released at t0 (seconds) with velocity v0. */
internal class FaqTransitions(val from: DoubleArray, val to: DoubleArray, val v0: DoubleArray, val t0: DoubleArray)

internal fun restTransitions(): FaqTransitions {
    val rest = DoubleArray(ROW_COUNT) { if (it == REST_ROW) 1.0 else 0.0 }
    return FaqTransitions(rest, rest.copyOf(), DoubleArray(ROW_COUNT), DoubleArray(ROW_COUNT))
}

/** `open` of row j at time tau, in the film or under a finger. */
internal fun openAt(mode: Int, tr: FaqTransitions, reduced: Boolean, j: Int, tau: Double): Double {
    if (mode == MODE_AUTO) return filmOpen(j, tau)
    val s = tau - tr.t0[j]
    if (reduced) return tr.from[j] + (tr.to[j] - tr.from[j]) * clamp01(s / REDUCED_FADE)
    return springTo(tr.from[j], tr.to[j], tr.v0[j], s)
}

/**
 * How far (dp) the element at row position `target` is pushed down by the answers of rows
 * first..last above it: each answer's height times its `open`, sampled FOLLOW_LAG later per row of
 * distance, so the rows below follow one after another.
 */
internal fun pushAt(
    mode: Int, tr: FaqTransitions, reduced: Boolean, heights: FloatArray,
    first: Int, last: Int, target: Int, tau: Double,
): Double {
    var sum = 0.0
    for (j in first..last) {
        val lag = if (reduced) 0.0 else FOLLOW_LAG * (target - j)
        sum += heights[j] * openAt(mode, tr, reduced, j, tau - lag)
    }
    return sum
}

/**
 * The transitions after a finger toggles row `tapped` at time `now`: every row whose target changes
 * starts a new spring from where it is, with the velocity it has. `mode`/`tau` describe the state
 * being left (the film or an earlier transition). tapped < 0: back to rest.
 */
internal fun retarget(mode: Int, tr: FaqTransitions, reduced: Boolean, tau: Double, now: Double, tapped: Int): FaqTransitions {
    val dt = 1.0 / 120
    val from = DoubleArray(ROW_COUNT)
    val to = DoubleArray(ROW_COUNT)
    val v0 = DoubleArray(ROW_COUNT)
    val t0 = DoubleArray(ROW_COUNT)
    var openRow = -1
    for (j in 0 until ROW_COUNT) {
        val heading = if (mode == MODE_AUTO) filmTarget(j, tau) else tr.to[j]
        if (heading > 0.5) openRow = j
    }
    // Tapping the open row closes it (nothing open).
    val next = if (tapped < 0) REST_ROW else if (tapped == openRow) -1 else tapped
    for (j in 0 until ROW_COUNT) {
        val heading = if (mode == MODE_AUTO) filmTarget(j, tau) else tr.to[j]
        val goal = if (j == next) 1.0 else 0.0
        val cur = openAt(mode, tr, reduced, j, tau)
        if (goal == heading && mode != MODE_AUTO) {
            // untouched: keep the spring it is on
            from[j] = tr.from[j]; to[j] = tr.to[j]; v0[j] = tr.v0[j]; t0[j] = tr.t0[j]
        } else {
            val before = openAt(mode, tr, reduced, j, tau - dt)
            from[j] = cur; to[j] = goal; v0[j] = (cur - before) / dt; t0[j] = now
        }
    }
    return FaqTransitions(from, to, v0, t0)
}
