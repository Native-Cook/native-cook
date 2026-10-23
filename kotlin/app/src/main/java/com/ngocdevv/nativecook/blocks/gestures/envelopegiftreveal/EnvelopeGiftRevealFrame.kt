package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

internal fun envelopeGiftRevealClamp01(value: Float): Float = value.coerceIn(0f, 1f)

internal fun envelopeGiftRevealEaseOut(value: Float): Float =
    1f - (1f - envelopeGiftRevealClamp01(value)).pow(3)

internal fun envelopeGiftRevealEaseInOut(value: Float): Float {
    val t = envelopeGiftRevealClamp01(value)
    return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
}

internal fun envelopeGiftRevealEaseOutBack(value: Float, overshoot: Float): Float {
    val t = envelopeGiftRevealClamp01(value) - 1f
    return 1f + (overshoot + 1f) * t * t * t + overshoot * t * t
}

internal fun envelopeGiftRevealFinishMs(progress: Float, reduced: Boolean): Float =
    if (reduced) REDUCED_MS else 260f + EnvelopeGiftRevealReveal.finishMs * (1f - progress)

internal class EnvelopeGiftRevealFrame {
    var now = 0.0
    var p = 0f
    var fly = 0f
    var after = -1f
    var reset = 0f
    var resetOut = 0f
    var resetIn = 0f
    var drop = 0f
    var flipT = 0f
    var breath = 0f
    var tilt0 = 0f

    fun update(engine: EnvelopeGiftRevealEngine, reduced: Boolean) {
        val clock = engine.clock
        var progress = engine.p.value
        var flight = 0f
        var elapsedAfter = -1f
        val committed = engine.commitAt >= 0.0
        if (committed) {
            val finish = envelopeGiftRevealFinishMs(engine.commitP0, reduced)
            val elapsed = (clock - engine.commitAt).toFloat()
            progress = engine.commitP0 +
                (1f - engine.commitP0) * envelopeGiftRevealEaseOut(elapsed / finish)
            elapsedAfter = elapsed - finish
            flight = if (reduced) {
                if (elapsedAfter >= 0f) 1f else 0f
            } else {
                EnvelopeGiftRevealReveal.flyEase.transform(
                    envelopeGiftRevealClamp01(elapsedAfter / EnvelopeGiftRevealReveal.flyMs),
                )
            }
        }
        val resetProgress = if (engine.resetAt < 0.0) {
            0f
        } else {
            envelopeGiftRevealClamp01(
                (clock - engine.resetAt).toFloat() /
                    (if (reduced) REDUCED_MS else EnvelopeGiftRevealReset.fadeMs),
            )
        }
        val dropProgress = if (elapsedAfter < 0f || reduced) {
            if (elapsedAfter >= 0f) 1f else 0f
        } else {
            envelopeGiftRevealClamp01(
                (elapsedAfter - 60f) / EnvelopeGiftRevealReveal.envelopeDropMs,
            )
        }
        val flip = if (elapsedAfter < 0f) {
            0f
        } else if (reduced) {
            1f
        } else {
            envelopeGiftRevealClamp01(
                (elapsedAfter - EnvelopeGiftRevealReveal.flyMs - EnvelopeGiftRevealReveal.flipDelay) /
                    EnvelopeGiftRevealReveal.flipMs,
            )
        }
        val breathSample = engine.breath
        val breathing = if (!committed && !reduced && breathSample < 700f) {
            sin(PI.toFloat() * breathSample / 700f) * EnvelopeGiftRevealOpen.breathDeg
        } else {
            0f
        }
        now = clock
        p = progress
        fly = flight
        after = elapsedAfter
        reset = resetProgress
        resetOut = envelopeGiftRevealClamp01(resetProgress / EnvelopeGiftRevealReset.outShare)
        resetIn = envelopeGiftRevealClamp01(
            (resetProgress - EnvelopeGiftRevealReset.inFrom) / (1f - EnvelopeGiftRevealReset.inFrom),
        )
        drop = dropProgress
        flipT = flip
        breath = breathing
        tilt0 = if (committed) engine.commitTilt0 else engine.tilt.value
    }
}
