package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * `LOOP` in `NocturneProgramCarousel.tsx`: the length of one `autoPlay` loop, in seconds. The reference
 * is a 15-second three-part story and two more seconds close the loop.
 */
const val NOCTURNE_PROGRAM_CAROUSEL_LOOP = 17.0

/** The helpers of `motion.ts` and the two timelines at the top of `NocturneProgramCarousel.tsx`. */
internal object NocturneProgramCarouselMotion {
    /** `ANCHORS`: the loop time each page rests at once a finger has settled it. */
    val Anchors = doubleArrayOf(4.3, 8.4, 12.0)

    /** `enterCurve`: the strong ease-out every reveal in the block follows. */
    val Enter = NocturneProgramCarouselBezier(0.23, 1.0, 0.32, 1.0)

    private val SlideXs = doubleArrayOf(0.0, 0.016, 0.033, 0.083, 0.116, 0.133, 0.15, 0.183, 0.25, 0.283, 0.35, 0.466, 0.683)
    private val SlideYs = doubleArrayOf(0.0, 0.033, 0.104, 0.278, 0.481, 0.566, 0.632, 0.736, 0.868, 0.906, 0.953, 0.991, 1.0)

    fun clamp(value: Double, low: Double = 0.0, high: Double = 1.0): Double = min(high, max(low, value))

    /** `segment`: how far [time] is through the span from [start] to [end], 0 to 1. */
    fun segment(time: Double, start: Double, end: Double): Double = clamp((time - start) / (end - start))

    /** `enter`: [Enter] evaluated at [value]. */
    fun enter(value: Double): Double = Enter.at(value)

    /** `rubber`: resistance past either end of a range from 0 to [limit]. */
    fun rubber(value: Double, limit: Double): Double {
        if (value < 0.0) return value / (1.0 + abs(value) * 4.0)
        if (value > limit) return limit + (value - limit) / (1.0 + (value - limit) * 4.0)
        return value
    }

    /** Reanimated's `interpolate(x, xs, ys, 'clamp')`: piecewise linear, held at both ends. */
    fun interpolate(x: Double, xs: DoubleArray, ys: DoubleArray): Double {
        if (x <= xs[0]) return ys[0]
        val last = xs.size - 1
        if (x >= xs[last]) return ys[last]
        var index = 1
        while (x > xs[index]) index += 1
        val fraction = (x - xs[index - 1]) / (xs[index] - xs[index - 1])
        return ys[index - 1] + (ys[index] - ys[index - 1]) * fraction
    }

    /** `sourceCardSlide`: incoming-card edge samples, including the long settling tail seen at 60 fps. */
    fun sourceCardSlide(time: Double, start: Double): Double = interpolate(time - start, SlideXs, SlideYs)

    /** `timelinePage`: the carousel's position, in pages (0 to 2), at loop time [time]. */
    fun timelinePage(time: Double): Double {
        if (time >= 16.1) return 0.0
        return sourceCardSlide(time, 4.817) + sourceCardSlide(time, 9.834)
    }
}
