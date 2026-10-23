package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

/**
 * One of the three final elements (title, message, button) rising in when the reveal shows, and
 * leaving again on reset. It is `withDelay(delay, withTiming(...))` of EnvelopeGiftReveal.tsx
 * finalStyle: a retarget starts from wherever the element is now, waits [delay], then eases
 * POLISH.easeOut over [duration], for both entrances and exits.
 */
internal class EnvelopeFinalFade(private val order: Int, private val reduced: Boolean) {
    private var opacityFrom = 0f
    private var opacityTo = 0f
    private var riseFrom = if (reduced) 0f else EnvelopePolish.textRise
    private var riseTo = riseFrom
    private var startMs = 0.0
    private var delayMs = 0f
    private var durationMs = 1f

    fun retarget(showFinal: Boolean, nowMs: Double) {
        val currentOpacity = opacity(nowMs)
        val currentRise = rise(nowMs)
        opacityFrom = currentOpacity
        riseFrom = currentRise
        opacityTo = if (showFinal) 1f else 0f
        riseTo = if (showFinal || reduced) 0f else EnvelopePolish.textRise
        startMs = nowMs
        delayMs = if (showFinal && !reduced) 400f + EnvelopeReveal.textDelay + order * EnvelopeReveal.stagger else 0f
        durationMs = if (showFinal) 320f else if (reduced) REDUCED_MS else EnvelopeReset.fadeMs
    }

    fun opacity(nowMs: Double): Float = opacityFrom + (opacityTo - opacityFrom) * eased(nowMs)

    /** Distance still to rise, in dp (positive is down). */
    fun rise(nowMs: Double): Float = riseFrom + (riseTo - riseFrom) * eased(nowMs)

    private fun eased(nowMs: Double): Float {
        val u = envelopeClamp01(((nowMs - startMs).toFloat() - delayMs) / durationMs)
        return EnvelopePolish.easeOut.transform(u)
    }
}
