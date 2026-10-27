package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.animation.core.Easing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Loop period in seconds (`LOOP` in `EmberDayReview.tsx`). Every frame is a function of the loop time `t` in `0..10`. */
const val EMBER_DAY_REVIEW_LOOP = 10

/** The loop time the block rests at when it does not play by itself (React Native starts `time` at 2). */
internal const val EMBER_DAY_REVIEW_REST = 2f

/** Reanimated's default layout-animation easing, `Easing.inOut(Easing.quad)`, used by the fades and the Reduce Motion cross-fade. */
internal val EmberQuadInOut = Easing { x -> if (x < 0.5f) 2f * x * x else -1f + (4f - 2f * x) * x }

/** Reanimated's `interpolate(x, input, output, 'clamp')`: linear between the knots, flat outside them. */
internal fun emberInterpolate(x: Float, input: FloatArray, output: FloatArray): Float {
    val last = input.size - 1
    if (x <= input[0]) {
        return output[0]
    }
    if (x >= input[last]) {
        return output[last]
    }
    for (i in 1..last) {
        if (x <= input[i]) {
            val fraction = (x - input[i - 1]) / (input[i] - input[i - 1])
            return output[i - 1] + (output[i] - output[i - 1]) * fraction
        }
    }
    return output[last]
}

private val HeroIn = floatArrayOf(0f, 0.25f, 0.4f, 0.6f, 0.8f, 9.6f, 10f)
private val HeroOut = floatArrayOf(0f, 0f, 0.62f, 0.94f, 1f, 1f, 0f)

/** The hero's opening, 0 to 1 (`heroStyle`, `followingStyle`: 0.25 to 0.8 s in, folded away 9.6 to 10 s). */
internal fun emberHeroProgress(t: Float) = emberInterpolate(t, HeroIn, HeroOut)

private val HaloOpacityIn = floatArrayOf(0.3f, 0.5f, 0.8f, 1.45f)
private val HaloOpacityOut = floatArrayOf(0f, 0.6f, 0.35f, 0f)
private val HaloScaleIn = floatArrayOf(0.3f, 0.7f, 1.4f)
private val HaloScaleOut = floatArrayOf(0.2f, 0.85f, 1.2f)

/** `haloStyle.opacity`. */
internal fun emberHaloOpacity(t: Float) = emberInterpolate(t, HaloOpacityIn, HaloOpacityOut)

/** `haloStyle` scale. */
internal fun emberHaloScale(t: Float) = emberInterpolate(t, HaloScaleIn, HaloScaleOut)

private val EndFadeIn = floatArrayOf(9.6f, 9.95f)
private val FadeOutOut = floatArrayOf(0f, 1f)

/** `interpolate(t, [9.6, 9.95], [0, 1])`: how far the content has faded for the loop's end (0 to 1). */
internal fun emberEndFade(t: Float) = emberInterpolate(t, EndFadeIn, FadeOutOut)

private val SummaryIn = floatArrayOf(0.85f, 1.4f)
private val SummaryRise = floatArrayOf(10f, 0f)

/** `summaryStyle.opacity`: resolves 0.85 to 1.4 s and leaves with the end fade. */
internal fun emberSummaryOpacity(t: Float) = emberInterpolate(t, SummaryIn, FadeOutOut) * (1f - emberEndFade(t))

/** `summaryStyle` translateY, in dp. */
internal fun emberSummaryRise(t: Float) = emberInterpolate(t, SummaryIn, SummaryRise)

/** `HistoryEntry` opacity: the rows enter 180 ms apart. */
internal fun emberHistoryOpacity(t: Float, index: Int): Float {
    val start = 0.72f + index * 0.18f
    return emberInterpolate(
        t,
        floatArrayOf(start, 1.12f + index * 0.18f, 9.6f, 9.95f),
        floatArrayOf(0f, 1f, 1f, 0f),
    )
}

/** `HistoryEntry` translateY, in dp. */
internal fun emberHistoryRise(t: Float, index: Int): Float {
    val start = 0.72f + index * 0.18f
    return emberInterpolate(t, floatArrayOf(start, 1.12f + index * 0.18f), SummaryRise)
}

private val GlowIn = floatArrayOf(0.4f, 0.9f, 9.6f, 10f)
private val GlowOut = floatArrayOf(0f, 1f, 1f, 0f)
private val GlowScaleIn = floatArrayOf(0.25f, 0.8f)
private val GlowScaleOut = floatArrayOf(0.6f, 1f)

/** The time part of `glowStyle.opacity`. */
internal fun emberGlowOpacity(t: Float) = emberInterpolate(t, GlowIn, GlowOut)

/** The time part of `glowStyle` scale: the glow grows from 60%. */
internal fun emberGlowScale(t: Float) = emberInterpolate(t, GlowScaleIn, GlowScaleOut)

private val PageIn = floatArrayOf(7f, 7.5f, 9.25f, 9.85f)
private val PageOut = floatArrayOf(0f, 1f, 1f, 0f)

/** `pageProgress` while the loop plays: the page slides to Today 7 to 7.5 s and back 9.25 to 9.85 s. */
internal fun emberPageProgress(t: Float) = emberInterpolate(t, PageIn, PageOut)

private val ReviewIn = floatArrayOf(4.55f, 4.6f, 4.7f, 4.8f, 4.95f, 6.03f, 6.1f, 6.2f, 6.35f)
private val ReviewOut = floatArrayOf(0f, 0.05f, 0.32f, 0.8f, 1f, 1f, 0.85f, 0.1f, 0f)

/** `reviewProgress` while the loop plays: the day drops 4.55 to 4.95 s and comes back 6.03 to 6.35 s. */
internal fun emberReviewProgress(t: Float) = emberInterpolate(t, ReviewIn, ReviewOut)

/** The delay of each star's turn behind the main one: the small ones are 120 and 200 ms behind (`offset` in `Star`). */
internal fun emberStarOffset(index: Int) = if (index == 1) 0f else if (index == 0) 0.12f else 0.2f

private val EmergeIn = floatArrayOf(0.48f, 0.6f, 0.82f, 1.4f)
private val EmergeOut = floatArrayOf(0f, 0.25f, 1f, 1f)
private val TurnIn = floatArrayOf(0.48f, 0.6f, 0.82f, 1.02f, 1.19f, 1.43f, 1.65f)
private val TurnOut = floatArrayOf(90f, 86f, 20f, -30f, -88f, -180f, -180f)
private val RollIn = floatArrayOf(0.48f, 0.8f, 1.03f, 1.2f, 1.45f, 1.65f)
private val RollOut = floatArrayOf(18f, -28f, 28f, 12f, -4f, 0f)

/** A star at its pose for the loop time: edge-on, face, edge-on, then a settled face (`Star` in React Native). */
internal class EmberDayReviewStarPose(
    val opacity: Float,
    val rollDegrees: Float,
    val scaleX: Float,
    val emerge: Float,
    val riseDp: Float,
)

/** `Star`'s animated style for [index] at loop time [t]; the settled pose under Reduce Motion. */
internal fun emberStarPose(t: Float, index: Int, reduced: Boolean): EmberDayReviewStarPose {
    if (reduced) {
        return EmberDayReviewStarPose(opacity = 1f, rollDegrees = 0f, scaleX = 1f, emerge = 1f, riseDp = 0f)
    }
    val local = t - emberStarOffset(index)
    val emerge = emberInterpolate(local, EmergeIn, EmergeOut)
    val turn = emberInterpolate(local, TurnIn, TurnOut)
    val roll = emberInterpolate(local, RollIn, RollOut)
    val opacity = min(1f, emerge * 3f) * (1f - emberEndFade(t))
    val scaleX = max(0.025f, abs(cos(turn * PI.toFloat() / 180f)))
    return EmberDayReviewStarPose(opacity, roll, scaleX, emerge, (1f - emerge) * 12f)
}
