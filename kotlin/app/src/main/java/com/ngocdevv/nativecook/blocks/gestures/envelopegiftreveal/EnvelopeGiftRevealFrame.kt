package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

internal fun envelopeClamp01(v: Float): Float = v.coerceIn(0f, 1f)

internal fun envelopeEaseOut(t: Float): Float = 1f - (1f - envelopeClamp01(t)).pow(3)

/** Ease-in-out cubic used by the scripted finger and the card's scale interpolation. */
internal fun envelopeEaseInOut(t: Float): Float {
    val c = envelopeClamp01(t)
    return if (c < 0.5f) 4f * c * c * c else 1f - (-2f * c + 2f).pow(3) / 2f
}

/** How long the commit takes to finish opening from progress p0 (use-envelope.ts finishMs). */
internal fun envelopeFinishMs(p0: Float, reduced: Boolean): Float =
    if (reduced) REDUCED_MS else 260f + EnvelopeReveal.finishMs * (1f - p0)

/** Everything the scene needs for one frame, from the clock (EnvelopeScene.tsx frameOf). */
internal class EnvelopeFrame {
    var now = 0.0
    var p = 0f
    var fly = 0f
    var after = -1f
    var reset = 0f
    var page = 0f
    var drop = 0f
    var breath = 0f
    var tilt0 = 0f

    fun update(engine: EnvelopeGiftRevealEngine, reduced: Boolean) {
        val clock = engine.clock
        var progress = engine.p.value
        var flight = 0f
        var elapsedAfter = -1f
        val committed = engine.commitAt >= 0.0
        if (committed) {
            val fin = envelopeFinishMs(engine.commitP0, reduced)
            val sinceCommit = (clock - engine.commitAt).toFloat()
            progress = engine.commitP0 + (1f - engine.commitP0) * envelopeEaseOut(sinceCommit / fin)
            elapsedAfter = sinceCommit - fin
            flight = if (reduced) {
                if (elapsedAfter >= 0f) 1f else 0f
            } else {
                EnvelopeReveal.flyEase.transform(
                    envelopeClamp01(elapsedAfter / EnvelopeReveal.flyMs),
                )
            }
        }
        val resetFrom = engine.resetAt
        val resetProgress = if (resetFrom < 0.0) {
            0f
        } else {
            envelopeClamp01((clock - resetFrom).toFloat() / (if (reduced) REDUCED_MS else EnvelopeReset.fadeMs))
        }
        val pageProgress = if (elapsedAfter < 0f) {
            0f
        } else {
            envelopeClamp01((elapsedAfter + 100f) / (if (reduced) REDUCED_MS else EnvelopeReveal.pageMs))
        }
        val dropProgress = if (elapsedAfter < 0f || reduced) {
            if (elapsedAfter >= 0f) 1f else 0f
        } else {
            envelopeClamp01((elapsedAfter - 60f) / EnvelopeReveal.envelopeDropMs)
        }
        val b = engine.breath
        val breathDeg = if (!committed && !reduced && b < 700f) {
            sin(PI.toFloat() * b / 700f) * EnvelopeOpen.breathDeg
        } else {
            0f
        }
        now = engine.visualClock
        p = progress
        fly = flight
        after = elapsedAfter
        reset = resetProgress
        page = pageProgress
        drop = dropProgress
        breath = breathDeg
        tilt0 = if (committed) engine.commitTilt0 else engine.tilt.value
    }
}
