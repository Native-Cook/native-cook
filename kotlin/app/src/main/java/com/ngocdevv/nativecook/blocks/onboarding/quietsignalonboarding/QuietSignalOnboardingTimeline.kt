package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import androidx.compose.animation.core.Easing
import kotlin.math.abs
import kotlin.math.floor

/** `LOOP`: the length of one `autoPlay` loop, in seconds. */
const val QUIET_SIGNAL_ONBOARDING_LOOP = 34.0

/**
 * The timeline of the five pages: `motion.ts` and the constants at the top of
 * `QuietSignalOnboarding.tsx`. Every function takes loop time in seconds, so any frame of the loop
 * is a function of one number.
 */
internal object QuietSignalOnboardingTimeline {
    const val LOOP = QUIET_SIGNAL_ONBOARDING_LOOP
    const val LAST_PAGE = 4

    /** `ANCHORS`: the loop time each page rests at when a touch or a page change settles on it. */
    val ANCHORS = doubleArrayOf(3.0, 11.0, 18.0, 25.0, 31.9)

    /** `STARTS`: when each page begins to appear. */
    val STARTS = doubleArrayOf(0.0, 9.0, 15.25, 22.0, 28.25)

    /** `ENDS`: when each page begins to leave. */
    val ENDS = doubleArrayOf(8.15, 14.4, 21.4, 27.5, 32.5)

    /** `FADE_ENDS`: when each page is gone. */
    val FADE_ENDS = doubleArrayOf(8.5, 14.8, 21.8, 27.9, 33.25)

    /** `enterCurve`, `Easing.bezierFn(.23, 1, .32, 1)`: the strong ease-out. */
    val EnterEasing: Easing = QuietSignalOnboardingBezier(0.23, 1.0, 0.32, 1.0)

    private val moveCurve = QuietSignalOnboardingBezier(0.77, 0.0, 0.175, 1.0)
    private val enterCurve = QuietSignalOnboardingBezier(0.23, 1.0, 0.32, 1.0)

    /** The keyframes of a page leaving: `interpolate(progress, [0, .0486, ...], [1, .86, ...])`. */
    private val leaveProgress = doubleArrayOf(0.0, 0.0486, 0.2857, 0.523, 0.762, 1.0)
    private val leaveOpacity = doubleArrayOf(1.0, 0.86, 0.4, 0.17, 0.06, 0.0)

    fun clamp(value: Double, low: Double = 0.0, high: Double = 1.0): Double = minOf(high, maxOf(low, value))

    /** `segment`: how far [time] has got from [start] to [end], 0 to 1. */
    fun segment(time: Double, start: Double, end: Double): Double = clamp((time - start) / (end - start))

    /** `ease`: `bezierFn(.77, 0, .175, 1)`, used for the emblem and the last page's copy. */
    fun ease(value: Double): Double = moveCurve.at(value)

    /** `enter`: `bezierFn(.23, 1, .32, 1)`. */
    fun enter(value: Double): Double = enterCurve.at(value)

    /** JavaScript's `Math.round`: halves go up, where Kotlin's `round` goes to the even number. */
    fun round(value: Double): Double = floor(value + 0.5)

    /** `rubber`: a drag past the first or last page meets resistance. */
    fun rubber(value: Double, max: Double): Double {
        if (value < 0) return value / (1 + abs(value) * 4)
        if (value > max) return max + (value - max) / (1 + (value - max) * 4)
        return value
    }

    /** `pageAt`: the page the loop is showing at [time]. */
    fun pageAt(time: Double): Int {
        if (time >= 33.25) return 0
        if (time >= 28.0) return 4
        if (time >= 21.75) return 3
        if (time >= 14.75) return 2
        if (time >= 8.5) return 1
        return 0
    }

    /**
     * `previewTime` as a phase of the loop: a finite value folded into 0 to [LOOP], anything else
     * that is not null counts as 0 (`usePlayback`).
     */
    fun previewPhase(previewTime: Double?): Double? {
        if (previewTime == null) return null
        if (!previewTime.isFinite()) return 0.0
        val remainder = previewTime % LOOP
        return if (remainder < 0) remainder + LOOP else remainder
    }

    /** The opacity of page [index] on the loop: it enters over 1.3 s and leaves along [leaveOpacity]. */
    fun sceneOpacity(index: Int, time: Double): Double {
        val entering = enter(segment(time, STARTS[index], STARTS[index] + 1.3))
        return entering * leaving(segment(time, ENDS[index], FADE_ENDS[index]))
    }

    /** How much of its copy page [index] has surfaced (1 is fully in focus), on the loop. */
    fun copyShown(index: Int, time: Double): Double {
        if (index == LAST_PAGE) return ease(segment(time, 29.15, 30.1))
        return enter(segment(time, STARTS[index] + 0.45, STARTS[index] + 1.8))
    }

    /** Page [index] is worth drawing on the loop from 0.1 s before it appears until it is gone. */
    fun isLive(index: Int, time: Double): Boolean = time >= STARTS[index] - 0.1 && time <= FADE_ENDS[index]

    /** `interpolate(progress, leaveProgress, leaveOpacity, 'clamp')`. */
    private fun leaving(progress: Double): Double {
        val x = clamp(progress)
        var i = 0
        while (i < leaveProgress.size - 2 && x > leaveProgress[i + 1]) i++
        val k = (x - leaveProgress[i]) / (leaveProgress[i + 1] - leaveProgress[i])
        return leaveOpacity[i] + (leaveOpacity[i + 1] - leaveOpacity[i]) * k
    }
}

/**
 * A cubic Bezier easing solved in double precision, the same curve as Reanimated's `Easing.bezier`
 * and CSS `cubic-bezier(x1, y1, x2, y2)`. Compose's own `CubicBezierEasing` stops at an error of
 * 0.001 in x, which is up to about 4 dp of drift on the steep start of the strong ease-out.
 */
private class QuietSignalOnboardingBezier(
    private val x1: Double,
    private val y1: Double,
    private val x2: Double,
    private val y2: Double,
) : Easing {
    override fun transform(fraction: Float): Float = at(fraction.toDouble()).toFloat()

    /** The curve's y at progress [x], clamped to 0 and 1 outside the unit interval. */
    fun at(x: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        return coordinate(y1, y2, solve(x))
    }

    private fun coordinate(p1: Double, p2: Double, t: Double): Double {
        val a = 1.0 - 3.0 * p2 + 3.0 * p1
        val b = 3.0 * p2 - 6.0 * p1
        val c = 3.0 * p1
        return ((a * t + b) * t + c) * t
    }

    private fun slope(p1: Double, p2: Double, t: Double): Double {
        val a = 1.0 - 3.0 * p2 + 3.0 * p1
        val b = 3.0 * p2 - 6.0 * p1
        val c = 3.0 * p1
        return 3.0 * a * t * t + 2.0 * b * t + c
    }

    /** The curve parameter whose x is [x]: a few Newton steps, then bisection if the slope is flat. */
    private fun solve(x: Double): Double {
        var t = x
        for (step in 0 until 8) {
            val error = coordinate(x1, x2, t) - x
            if (abs(error) < 1e-10) return t
            val derivative = slope(x1, x2, t)
            if (abs(derivative) < 1e-6) break
            t -= error / derivative
        }
        var low = 0.0
        var high = 1.0
        t = x
        for (step in 0 until 60) {
            val value = coordinate(x1, x2, t)
            if (abs(value - x) < 1e-12) return t
            if (value < x) low = t else high = t
            t = (low + high) / 2.0
        }
        return t
    }
}
