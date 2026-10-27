package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** `LOOP`: the length of one `autoPlay` loop, in seconds. */
const val SPECTRUM_NEWS_CAROUSEL_LOOP = 22.0

/** `ANCHORS`: the loop time at which each page rests; `timelinePage` of anchor `n` is page `n`. */
internal val SpectrumNewsCarouselAnchors = doubleArrayOf(2.5, 7.5, 10.0, 11.75, 14.0)

/** The window the welcome page grows from, as fractions of the screen (`WelcomeFrame`). */
internal class SpectrumNewsCarouselFrame(val x: Double, val y: Double, val width: Double, val height: Double) {
    companion object {
        /** The whole screen: what every state except the intro and outro shows. */
        val Full = SpectrumNewsCarouselFrame(0.0, 0.0, 1.0, 1.0)
    }
}

/** The numbers of `motion.ts` and the timeline of `SpectrumNewsCarousel.tsx`, as plain functions of loop time. */
internal object SpectrumNewsCarouselMotion {
    private val enterCurve = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    // `sourceSwipe`: normalized positions sampled from the source's 60 fps landmarks.
    private val swipeTimes = doubleArrayOf(0.0, 0.033, 0.05, 0.067, 0.1, 0.133, 0.167, 0.2, 0.233, 0.267, 0.3, 0.333, 0.383)
    private val swipeValues = doubleArrayOf(0.0, 0.104, 0.13, 0.17, 0.333, 0.51, 0.672, 0.791, 0.876, 0.931, 0.963, 0.985, 1.0)

    // The three reverse swipes of `timelinePage`.
    private val backFirstTimes = doubleArrayOf(16.2, 16.25, 16.3, 16.35, 16.4, 16.45, 16.5, 16.55, 16.65)
    private val backFirstValues = doubleArrayOf(0.0, 0.093, 0.304, 0.563, 0.77, 0.893, 0.959, 0.989, 1.0)
    private val backSecondTimes = doubleArrayOf(17.1, 17.167, 17.25, 17.5, 17.75, 18.0, 18.05, 18.1, 18.15, 18.2, 18.25, 18.3, 18.35, 18.45)
    private val backSecondValues = doubleArrayOf(0.0, 0.026, 0.059, 0.119, 0.2, 0.252, 0.281, 0.33, 0.46, 0.667, 0.822, 0.911, 0.963, 1.0)
    private val backThirdTimes = doubleArrayOf(19.0, 19.083, 19.167, 19.3, 19.5, 19.75, 20.0, 20.05, 20.1, 20.15, 20.2, 20.25, 20.3, 20.4)
    private val backThirdValues = doubleArrayOf(0.0, 0.011, 0.043, 0.074, 0.119, 0.23, 0.319, 0.47, 0.574, 0.707, 0.844, 0.93, 0.974, 1.0)

    // `welcomeFrame` knots, in seconds, and the window at each of them.
    private val frameKnots = doubleArrayOf(0.0, 0.75, 1.0, 1.25, 1.5, 1.75)
    private val frameX = doubleArrayOf(0.39, 0.3593, 0.2185, 0.0926, 0.0259, 0.0)
    private val frameY = doubleArrayOf(0.2621, 0.2586, 0.1575, 0.0634, 0.0274, 0.0)
    private val frameWidth = doubleArrayOf(0.22, 0.2815, 0.563, 0.8148, 0.9481, 1.0)
    private val frameHeight = doubleArrayOf(0.13, 0.1575, 0.4846, 0.7705, 0.9264, 1.0)
    private val outroTimes = doubleArrayOf(21.4, 22.0)
    private val outroSamples = doubleArrayOf(1.75, 0.0)

    fun clamp(value: Double, lower: Double = 0.0, upper: Double = 1.0): Double = min(upper, max(lower, value))

    fun segment(time: Double, start: Double, end: Double): Double = clamp((time - start) / (end - start))

    /** `enter`: the strong ease-out `bezier(.23, 1, .32, 1)`. */
    fun enter(value: Double): Double = enterCurve.transform(value.toFloat()).toDouble()

    /** `rubber`: past either end of the pages a drag follows the finger ever more reluctantly. */
    fun rubber(value: Double, upper: Double): Double {
        if (value < 0.0) return value / (1.0 + abs(value) * 4.0)
        if (value > upper) return upper + (value - upper) / (1.0 + (value - upper) * 4.0)
        return value
    }

    /** JavaScript's `Math.round`: halves go up, where Kotlin's `round` goes to the even number. */
    fun jsRound(value: Double): Double = floor(value + 0.5)

    /** Reanimated's `interpolate(value, input, output, 'clamp')`. */
    fun interpolate(value: Double, input: DoubleArray, output: DoubleArray): Double {
        val last = input.size - 1
        if (value <= input[0]) return output[0]
        if (value >= input[last]) return output[last]
        var index = 1
        while (value > input[index]) index += 1
        val fraction = (value - input[index - 1]) / (input[index] - input[index - 1])
        return output[index - 1] + (output[index] - output[index - 1]) * fraction
    }

    private fun sourceSwipe(time: Double, start: Double): Double = interpolate(time - start, swipeTimes, swipeValues)

    /** `timelinePage`: the (fractional) page the loop shows at [t]; four swipes forward, then four back. */
    fun timelinePage(t: Double): Double {
        return sourceSwipe(t, 6.45) + sourceSwipe(t, 8.7) + sourceSwipe(t, 11.117) + sourceSwipe(t, 12.833) -
            sourceSwipe(t, 15.4) -
            interpolate(t, backFirstTimes, backFirstValues) -
            interpolate(t, backSecondTimes, backSecondValues) -
            interpolate(t, backThirdTimes, backThirdValues)
    }

    /** The window of the intro (0 to 1.75 s) and of the outro (21.4 to 22 s); [t] is loop time. */
    fun welcomeFrame(t: Double): SpectrumNewsCarouselFrame {
        val sample = if (t > 21.4) interpolate(t, outroTimes, outroSamples) else t
        return SpectrumNewsCarouselFrame(
            x = interpolate(sample, frameKnots, frameX),
            y = interpolate(sample, frameKnots, frameY),
            width = interpolate(sample, frameKnots, frameWidth),
            height = interpolate(sample, frameKnots, frameHeight),
        )
    }

    /** `welcomeGlow`: the warm field fades in from 0.6 s and out again from 21.6 s. */
    fun welcomeGlow(t: Double): Double = enter(segment(t, 0.6, 0.75)) * (1.0 - enter(segment(t, 21.6, 22.0)))

    /** The welcome page's own fade (`intro`): in over the first half second, out over the last 0.4. */
    fun welcomeIntro(t: Double): Double = enter(segment(t, 0.0, 0.5)) * (1.0 - enter(segment(t, 21.6, 22.0)))

    /** `previewPhase`: [previewTime] folded into one loop, or null when the loop is not frozen. */
    fun previewPhase(previewTime: Double?): Double? {
        if (previewTime == null) return null
        val remainder = if (previewTime.isFinite()) previewTime % SPECTRUM_NEWS_CAROUSEL_LOOP else 0.0
        return if (remainder < 0.0) remainder + SPECTRUM_NEWS_CAROUSEL_LOOP else remainder
    }
}
