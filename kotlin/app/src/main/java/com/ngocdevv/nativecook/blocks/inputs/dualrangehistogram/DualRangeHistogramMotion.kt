package com.ngocdevv.nativecook.blocks.inputs.dualrangehistogram

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// All the motion of the Dual Range Histogram as pure functions of loop time, one to one with React Native's
// dualRangeMotion.ts. Positions are `u`, 0 … 1 across the price axis.

const val LOOP = 8.0

internal const val MODE_AUTO = 0
internal const val MODE_MANUAL = 1
internal const val MODE_RETURN = 2

internal const val IDLE_S = 2.0 // after the last touch
internal const val RETURN_S = 0.7

internal const val THUMB = 30f
internal const val TRACK_H = 4f
internal const val TRACK_AREA = 44f // height of the band holding the track and the thumbs
internal const val TAG_W = 72f
internal const val TAG_H = 30f
internal const val TAG_GAP = 6f
internal const val GRAB_R = 38f // dp around a thumb where a touch picks it up
internal const val TAP_SLOP = 8f

internal const val BAR_GAP = 2f
internal const val BAR_RADIUS = 2.5f
internal const val SINK = 0.58f // height of a bar outside the range
internal const val LIFT_PT = 7f // bars next to a held thumb rise
internal const val LIFT_SIGMA = 20f

internal const val SQUASH_X = 1.18f
internal const val SQUASH_Y = 0.86f
internal const val CONTACT_SHARE = 0.3 // how far two thumbs in contact push each other, per unit of overlap
internal const val FLING_S = 0.12 // the release lands where the velocity would carry it in this time

/** Reanimated's `withSpring({ mass: 1, stiffness, damping })`. */
internal class SpringSpec(val stiffness: Double, val damping: Double)

// response 0.42 s, damping ratio 0.68
internal val RELEASE_SPRING = SpringSpec(223.8, 20.35)
internal val PRESS_SPRING = SpringSpec(520.0, 30.0)

private const val FILM_RESPONSE = 0.42
private const val FILM_RATIO = 0.62

internal fun clamp(v: Double, a: Double, b: Double) = min(max(v, a), b)
internal fun clampF(v: Float, a: Float, b: Float) = min(max(v, a), b)
internal fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a), 0.0, 1.0)
internal fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
internal fun mixF(a: Float, b: Float, k: Float) = a + (b - a) * k
internal fun smooth(k: Double) = k * k * (3 - 2 * k)
internal fun easeInOutCubic(k: Double) = if (k < 0.5) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2

/** Up over a → b, held, down over c → d. */
internal fun win(t: Double, a: Double, b: Double, c: Double, d: Double) =
    smooth(seg(t, a, b)) * (1 - smooth(seg(t, c, d)))

/** A damped spring released at rest from `from`, s seconds ago. */
internal fun springAt(s: Double, from: Double, to: Double): Double {
    if (s <= 0) return from
    val w = 2 * PI / FILM_RESPONSE
    val zw = FILM_RATIO * w
    val wd = w * sqrt(1 - FILM_RATIO * FILM_RATIO)
    val a = from - to
    return to + exp(-zw * s) * (a * cos(wd * s) + (zw * a / wd) * sin(wd * s))
}

/** How far a finger's overshoot `over` (in u) moves a thumb past its limit. */
internal fun rubber(over: Double) = over * 0.55 / (1 + over / 0.09)

internal fun constrain(u: Double, lo: Double, hi: Double) = when {
    u < lo -> lo - rubber(lo - u)
    u > hi -> hi + rubber(u - hi)
    else -> u
}

/** Two thumbs pressed together push each other apart instead of crossing. */
internal fun shownLow(low: Double, high: Double, gap: Double) = low - CONTACT_SHARE * max(0.0, low + gap - high)
internal fun shownHigh(low: Double, high: Double, gap: Double) = high + CONTACT_SHARE * max(0.0, low + gap - high)

internal class Film(
    val restLow: Double,
    val restHigh: Double,
    val lowHold: Double,
    val lowOver: Double,
    val highLimit: Double,
    val highFinger: Double,
    val highOver: Double,
)

internal fun makeFilm(restLow: Double, restHigh: Double, gap: Double): Film {
    val lowHold = max(0.02, restLow - 0.1)
    val highLimit = lowHold + gap
    return Film(
        restLow = restLow,
        restHigh = restHigh,
        lowHold = lowHold,
        lowOver = lowHold - 0.012,
        highLimit = highLimit,
        highFinger = highLimit - 0.055,
        highOver = min(1.0, restHigh + 0.02),
    )
}

internal fun filmLow(t: Double, f: Film): Double = when {
    t < 0.85 -> f.restLow
    t < 1.75 -> mix(f.restLow, f.lowOver, easeInOutCubic(seg(t, 0.85, 1.75)))
    t < 5.9 -> springAt(t - 1.75, f.lowOver, f.lowHold)
    t < 6.7 -> mix(f.lowHold, f.restLow, easeInOutCubic(seg(t, 5.9, 6.7)))
    else -> f.restLow
}

internal fun filmHigh(t: Double, f: Film): Double {
    val held = f.highLimit - rubber(f.highLimit - f.highFinger)
    return when {
        t < 2.3 -> f.restHigh
        t < 3.55 -> {
            val finger = mix(f.restHigh, f.highFinger, easeInOutCubic(seg(t, 2.3, 3.55)))
            if (finger < f.highLimit) f.highLimit - rubber(f.highLimit - finger) else finger
        }
        t < 3.7 -> held
        t < 4.35 -> springAt(t - 3.7, held, f.highLimit)
        t < 5.4 -> mix(f.highLimit, f.highOver, easeInOutCubic(seg(t, 4.35, 5.4)))
        t < 6.85 -> springAt(t - 5.4, f.highOver, f.restHigh)
        else -> f.restHigh
    }
}

internal fun filmPressLow(t: Double) = win(t, 0.65, 0.8, 1.75, 1.9) + win(t, 5.75, 5.9, 6.7, 6.85)

internal fun filmPressHigh(t: Double) = win(t, 2.15, 2.3, 3.7, 3.85) + win(t, 4.2, 4.35, 5.4, 5.55)

internal fun filmGhost(t: Double) =
    win(t, 0.45, 0.65, 1.8, 2.0) + win(t, 1.98, 2.15, 3.75, 3.95) + win(t, 4.02, 4.2, 5.45, 5.62) + win(t, 5.6, 5.75, 6.75, 6.95)

/** 0: the ghost finger is on the low thumb, 1: on the high one. */
internal fun filmGhostSide(t: Double) = if (t >= 1.99 && t < 5.61) 1 else 0

/** Odometer position of one digit column: the lowest column turns freely, the others only as the one below wraps. */
internal fun digitAt(value: Double, place: Int, lowest: Boolean): Double {
    val v = max(0.0, value)
    if (lowest) return (v / place) % 10
    val below = (v % place) / place
    return (floor(v / place) % 10) + smooth(seg(below, 0.88, 1.0))
}

/** Listings between two positions, counting a bin by the share of it inside the range. */
internal fun rangeCount(a: FloatArray, b: FloatArray, morph: Double, low: Double, high: Double): Double {
    val n = b.size
    var sum = 0.0
    for (i in 0 until n) {
        val share = clamp(min(high * n, i + 1.0) - max(low * n, i.toDouble()), 0.0, 1.0)
        if (share > 0) sum += mix(a.getOrElse(i) { 0f }.toDouble(), b[i].toDouble(), morph) * share
    }
    return sum
}

/** Reanimated `Easing.bezier`, evaluated like CSS cubic-bezier. */
internal fun cubicBezier(x1: Double, y1: Double, x2: Double, y2: Double, x: Double): Double {
    if (x <= 0) return 0.0
    if (x >= 1) return 1.0
    fun bx(t: Double) = 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t
    fun by(t: Double) = 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t
    var lo = 0.0
    var hi = 1.0
    var t = x
    repeat(24) {
        if (bx(t) < x) lo = t else hi = t
        t = (lo + hi) / 2
    }
    return by(t)
}

/** The bins' morph and the count's roll: `Easing.bezier(0.65, 0, 0.35, 1)`. */
internal fun easeMorph(k: Double) = cubicBezier(0.65, 0.0, 0.35, 1.0, k)
