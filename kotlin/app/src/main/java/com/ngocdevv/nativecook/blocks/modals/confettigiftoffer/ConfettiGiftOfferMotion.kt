package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/** One period of the autoplay loop, in seconds (`LOOP` in ConfettiGiftOffer.tsx:17). */
const val CONFETTI_GIFT_OFFER_LOOP = 9.5

/** Where the clock rests while Reduce Motion is on: the settled offer screen (`useSceneClock(…, 1.5, …)`). */
internal const val CONFETTI_GIFT_OFFER_SETTLED = 1.5

/** The loop time the account screen is fully gone and the gift is coming back (`SOURCE_END`-style tail). */
internal const val CONFETTI_GIFT_OFFER_SOURCE_END = 8.4

/** The screens of the story (`Stage` in ConfettiGiftOffer.tsx:37). */
internal enum class ConfettiGiftOfferStage { Gift, Opening, Offer, Exit, Signup, Complete, Dismissed }

/** The two sign-in buttons on the account screen. */
enum class ConfettiGiftOfferProvider { Apple, Google }

/** `ENTER` / `EASE_OUT` in ConfettiGiftOffer.tsx:19, cubic-bezier(0.23, 1, 0.32, 1). */
internal val ConfettiGiftOfferEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

internal fun confettiGiftOfferEnter(x: Double): Double = ConfettiGiftOfferEaseOut.transform(x.toFloat()).toDouble()

/** `fraction` (ConfettiGiftOffer.tsx:20): 0 before [a], 1 from [b] on, linear between. */
internal fun confettiGiftOfferFraction(t: Double, a: Double, b: Double): Double {
    if (!t.isFinite()) return 0.0
    if (t >= b - 1e-9) return 1.0
    return min(1.0, max(0.0, (t - a) / (b - a)))
}

/** `phase` (motion.ts:6): a smoothstep from [start] to [end]. */
internal fun confettiGiftOfferPhase(t: Double, start: Double, end: Double): Double {
    val x = min(1.0, max(0.0, (t - start) / (end - start)))
    return x * x * (3 - 2 * x)
}

/** `normalizeSceneTime` (motion.ts:13): a time folded into one period. */
internal fun confettiGiftOfferNormalize(value: Double, period: Double): Double {
    if (!value.isFinite() || !period.isFinite() || period <= 0.0) return 0.0
    val remainder = value % period
    return if (remainder < 0) remainder + period else remainder
}

/** Piecewise-linear lookup with clamped ends: Reanimated's `interpolate(t, xs, ys, 'clamp')`. */
private fun interpolate(t: Double, xs: DoubleArray, ys: DoubleArray): Double {
    if (t <= xs.first()) return ys.first()
    if (t >= xs.last()) return ys.last()
    var i = 1
    while (i < xs.size && t > xs[i]) i++
    val span = xs[i] - xs[i - 1]
    return ys[i - 1] + (ys[i] - ys[i - 1]) * ((t - xs[i - 1]) / span)
}

private val PUSH_TIMES = doubleArrayOf(4.5, 4.5167, 4.5333, 4.55, 4.5667, 4.5833, 4.6, 4.6333, 4.6667, 4.7, 4.75, 4.8, 4.8333, 4.9)
private val PUSH_VALUES = doubleArrayOf(0.0, .068, .183, .294, .407, .503, .546, .731, .832, .894, .95, .978, .989, 1.0)

/** Incoming-screen edge measured from source frames 270 to 290 (ConfettiGiftOffer.tsx:21). */
internal fun confettiGiftOfferSourcePush(t: Double): Double = interpolate(t, PUSH_TIMES, PUSH_VALUES)

private val SHEET_TIMES = doubleArrayOf(3.167, 3.2, 3.2167, 3.2333, 3.25, 3.2667, 3.2833, 3.3, 3.3333, 3.35, 3.3833, 3.4167, 3.4333)
private val SHEET_VALUES = doubleArrayOf(0.0, .30, .377, .499, .601, .685, .756, .815, .903, .935, .978, .998, 1.0)

/** How far the skip sheet has risen (0 to 1), from measured positions (ConfettiGiftOffer.tsx:26). */
internal fun confettiGiftOfferSourceSheet(t: Double): Double = interpolate(t, SHEET_TIMES, SHEET_VALUES)

/** Gift opacity on the loop: solid until 0.583 s, back from 8.75 to 9.1 s (ConfettiGiftOffer.tsx:30). */
internal fun confettiGiftOfferSourceGiftAlpha(t: Double): Double =
    if (t < .583) 1.0 else confettiGiftOfferPhase(t, 8.75, 9.1)

private val OFFER_TIMES = doubleArrayOf(.583, .633, .65, .667, .683, .7, .75, .8, .85, .9, .95, 1.0, 1.05, 1.0833, 1.1)
private val OFFER_VALUES = doubleArrayOf(0.0, .097, .124, .171, .226, .263, .359, .479, .581, .682, .779, .871, .954, 1.0, 1.0)

/** Neutral ticket luminance measured at source frames 38 to 65, independent of scale (ConfettiGiftOffer.tsx:31). */
internal fun confettiGiftOfferSourceOfferAlpha(t: Double): Double =
    interpolate(t, OFFER_TIMES, OFFER_VALUES) * (1 - confettiGiftOfferPhase(t, 4.9, 4.92))

/** Account screen opacity: in from 4.5 s, out 8.4 to 8.75 s (ConfettiGiftOffer.tsx:36). */
internal fun confettiGiftOfferSourceSignupAlpha(t: Double): Double =
    (if (t >= 4.5) 1.0 else 0.0) * (1 - confettiGiftOfferPhase(t, CONFETTI_GIFT_OFFER_SOURCE_END, 8.75))

/** `stageAt` (ConfettiGiftOffer.tsx:40): which screen the loop is on at time [time]. */
internal fun confettiGiftOfferStageAt(time: Double): ConfettiGiftOfferStage {
    val t = time + 1e-9
    return when {
        t < .583 || t >= 8.75 -> ConfettiGiftOfferStage.Gift
        t < 1.1 -> ConfettiGiftOfferStage.Opening
        t < 3.167 -> ConfettiGiftOfferStage.Offer
        t < 4.9 -> ConfettiGiftOfferStage.Exit
        else -> ConfettiGiftOfferStage.Signup
    }
}

/** The ticket's faint "%" watermark as left %, top %, rotation (ConfettiGiftOffer.tsx:89). */
internal val ConfettiGiftOfferPercents: List<Triple<Float, Float, Float>> = listOf(
    Triple(6f, 6f, -18f), Triple(84f, 5f, 14f), Triple(20f, 22f, 10f), Triple(74f, 24f, -12f),
    Triple(6f, 44f, 16f), Triple(88f, 42f, -20f), Triple(30f, 9f, -8f), Triple(62f, 40f, 18f),
)

/** The four paper colours (ConfettiGiftOffer.tsx:63). */
private val CONFETTI_COLORS = listOf(Color(0xFF3E7BF5), Color(0xFFEF5A4E), Color(0xFF7FD8BC), Color(0xFFF4C43E))

/** Fixed numbers of one confetti strip, derived from its index exactly as `Confetti` does (ConfettiGiftOffer.tsx:65). */
internal class ConfettiGiftOfferStrip(val index: Int) {
    val seed: Double = ((index * 137 + 31) % 101) / 101.0
    val verticalSeed: Double = ((index * 47 + 17) % 103) / 103.0
    val depth: Double = if (index % 5 == 0) .62 else if (index % 3 == 0) .82 else 1.0
    val width: Double = (if (index % 3 != 0) 11 else 14) * depth
    val height: Double = (if (index % 3 != 0) 30 else 36) * depth
    val color: Color = CONFETTI_COLORS[index % 4]
}

/** The 56 strips of one burst (`Array.from({ length: 56 })`, ConfettiGiftOffer.tsx:252). */
internal val ConfettiGiftOfferStrips: List<ConfettiGiftOfferStrip> = List(56) { ConfettiGiftOfferStrip(it) }
