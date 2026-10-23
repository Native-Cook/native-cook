package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

internal data class PtsStage(
    val width: Float,
    val height: Float,
    val insetTop: Float,
)

internal data class PtsPageFrame(
    val x: Float,
    val y: Float,
    val scale: Float,
    val clipHeight: Float,
    val width: Float,
    val height: Float,
    val radius: Float,
    val trim: Float,
)

internal fun ptsMix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun ptsClamp01(value: Float): Float = value.coerceIn(0f, 1f)

internal fun ptsCardWidth(stage: PtsStage, lift: Float = 1f): Float =
    stage.width * ptsMix(PtsLayout.thinkWidthRatio, PtsLayout.cardWidthRatio, lift)

internal fun ptsCardHeight(lift: Float = 1f): Float =
    ptsMix(PtsLayout.thinkHeight, PtsLayout.cardHeight, lift)

internal fun ptsCardScale(stage: PtsStage, lift: Float = 1f): Float =
    ptsCardWidth(stage, lift) / stage.width

internal fun ptsTopTrim(stage: PtsStage): Float =
    max(0f, stage.insetTop - PtsLayout.cardTopTrim)

/** Where the page sits for progress [p], including the thinking-card breathing scale. */
internal fun ptsPageFrame(
    stage: PtsStage,
    p: Float,
    lift: Float,
    driftX: Float,
    driftY: Float,
    breath: Float = 0f,
): PtsPageFrame {
    val cardScale = ptsCardScale(stage, lift)
    val scale = ptsMix(1f, cardScale, p) * (1f + breath)
    val top = ptsMix(PtsLayout.thinkTop, PtsLayout.summaryTop, lift)
    val clipHeight = max(1f, ptsMix(stage.height, ptsCardHeight(lift) / cardScale, p))
    val grow = ptsMix(1f, cardScale, p) * breath
    val x = ptsMix(0f, (stage.width - ptsCardWidth(stage, lift)) / 2f, p) +
        driftX - stage.width * grow / 2f
    val y = ptsMix(0f, top, p) + driftY - clipHeight * grow / 2f
    val radius = ptsClamp01(abs(p) / 0.06f) *
        ptsMix(PtsLayout.deviceRadius, PtsLayout.cardRadius, ptsClamp01(p))

    return PtsPageFrame(
        x = x,
        y = y,
        scale = scale,
        clipHeight = clipHeight,
        width = stage.width * scale,
        height = clipHeight * scale,
        radius = radius,
        trim = ptsTopTrim(stage) * ptsClamp01(p),
    )
}

internal fun ptsBreathing(think: Float, clock: Float, amplitude: Float, period: Number): Float =
    think * amplitude * sin((clock / period.toFloat()) * (PI * 2.0)).toFloat()

internal fun ptsCardTop(lift: Float): Float =
    ptsMix(PtsLayout.thinkTop, PtsLayout.summaryTop, lift)
