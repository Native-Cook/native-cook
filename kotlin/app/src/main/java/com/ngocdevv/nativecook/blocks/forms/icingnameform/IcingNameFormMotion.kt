package com.ngocdevv.nativecook.blocks.forms.icingnameform

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 Icing Name Form — the autoPlay script; every value is a pure function of the loop time t.

   0.0 – 1.4   rest: "Happy birthday" piped in chocolate, the nozzle resting at the upper right.
   1.4 – 2.2   a palette knife sweeps left to right and lifts the writing off; the field empties.
   2.0 – 2.35  the nozzle comes down to the cake; "Noor" is typed (2.25 – 2.55).
   2.35 – 5.4  "Noor" is piped stroke by stroke, the nozzle lifting between strokes.
   5.5 – 5.95  the nozzle goes back to rest.
   5.6 – 6.4   the Rose chip is chosen: rose icing flows through the letters (5.65 – 6.35).
   6.4 – 7.4   hold.
   7.4 – 8.5   this cake slides out to the left with a turn, a fresh cake with "Happy birthday"
               slides in from the right; the field, chip and price go back (7.9).
   8.5 – 9.0   rest.
*/

internal const val LOOP = 9f
internal const val REST_TEXT = "Happy birthday"
internal const val SECOND_TEXT = "Noor"
private const val PIPE_FROM = 2.35f
private const val PIPE_TO = 5.4f
internal const val PRICE_ROLL = 0.45f

internal val IcingTextScript = listOf(
    0f to REST_TEXT, 1.5f to "Happy birth", 1.65f to "Happy", 1.8f to "Hap", 1.95f to "",
    2.25f to "N", 2.35f to "No", 2.45f to "Noo", 2.55f to SECOND_TEXT, 7.9f to REST_TEXT,
)

/** Price in cents over the loop: base cake $38, 50 c a letter, $1 for a coloured icing. */
private val PriceScript = listOf(0f to 4450, 1.95f to 3800, 2.6f to 4000, 5.7f to 4100, 7.9f to 4450)

internal fun icSeg(t: Float, a: Float, b: Float) = min(1f, max(0f, (t - a) / (b - a)))

internal fun icEaseInOut(u: Float) = if (u < 0.5f) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2

private fun easeOutBack(u: Float): Float {
    val c1 = 0.9f
    return 1 + (c1 + 1) * (u - 1).pow(3) + c1 * (u - 1).pow(2)
}

internal fun icScriptIndex(t: Float): Int {
    var i = 0
    for (k in IcingTextScript.indices) if (t >= IcingTextScript[k].first) i = k
    return i
}

/** The price: from cents, to cents, progress 0..1. */
internal fun icPriceAt(t: Float, out: FloatArray) {
    var i = 0
    for (k in PriceScript.indices) if (t >= PriceScript[k].first) i = k
    out[0] = (if (i == 0) PriceScript[PriceScript.size - 1].second else PriceScript[i - 1].second).toFloat()
    out[1] = PriceScript[i].second.toFloat()
    out[2] = if (i == 0) 1f else icEaseInOut(icSeg(t, PriceScript[i].first, PriceScript[i].first + PRICE_ROLL))
}

/** Knife position across the cake, -1 (left edge) → 1 (right edge); -2 when it is away. */
internal fun icKnifeAt(t: Float): Float {
    if (t < 1.4f || t > 2.2f) return -2f
    return -1.35f + 2.7f * icEaseInOut(icSeg(t, 1.4f, 2.2f))
}

internal fun icPipeProgress(t: Float) = icSeg(t, PIPE_FROM, PIPE_TO)

/** Rose flowing through the letters, 0..1 of their ink length. */
internal fun icRoseFlow(t: Float) = icEaseInOut(icSeg(t, 5.65f, 6.35f))

/** Chip ring position (0 Chocolate, 1 Rose). */
internal fun icChipPosition(t: Float) = easeOutBack(icSeg(t, 5.6f, 5.9f)) * (1 - easeOutBack(icSeg(t, 7.9f, 8.2f)))

/** The cake hand-over: 0 → the Noor cake is centred, 1 → the next cake is. */
internal fun icSlideAt(t: Float) = icEaseInOut(icSeg(t, 7.4f, 8.5f))

/** 0 = nozzle at rest, 1 = nozzle at work on the cake. */
internal fun icNozzleWork(t: Float) = icEaseInOut(icSeg(t, 2.0f, 2.35f)) * (1 - icEaseInOut(icSeg(t, 5.5f, 5.95f)))

internal fun icLetterCount(text: String) = icingChars(text).count { it != " " }

internal fun icPriceFor(text: String, colour: Int) = 3800 + 50 * icLetterCount(text) + if (colour == 0) 0 else 100
