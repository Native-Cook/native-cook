package com.ngocdevv.nativecook.blocks.text.embroideryheadline

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.max
import kotlin.math.pow

/*
 Embroidery Headline — the autoPlay script, a pure function of the loop time t (as in React Native).

   0.0 – 1.2   rest: "Slow made." satin-stitched on linen, the subheading below.
   1.2 – 2.2   the letters are unpicked left to right, each in reverse; the subheading fades (1.2 – 1.6).
   1.75 – 6.6  the letters are stitched again, each letter's time in proportion to its threads.
   6.6 – 7.0   the French knot pops with a damped overshoot.
   6.9 – 7.5   the subheading rises in (ease-out); hold to 9.0 = rest.
*/

internal const val LOOP = 9f
private const val UNPICK_FROM = 1.2f
private const val UNPICK_TO = 2.2f
private const val STITCH_FROM = 1.75f
private const val STITCH_TO = 6.6f
private const val KNOT_FROM = 6.6f
private const val KNOT_TO = 7.0f

internal fun embSeg(t: Float, a: Float, b: Float) = min(1f, max(0f, (t - a) / (b - a)))

internal fun embEaseInOut(u: Float) = if (u < 0.5f) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2

internal fun embEaseOut(u: Float) = 1 - (1 - u).pow(3)

/** A spring-like pop for the knot: 0.55 → 1 with one soft overshoot. */
internal fun embPop(u: Float): Float {
    if (u <= 0) return 0.55f
    if (u >= 1) return 1f
    return 1 - 0.45f * exp(-6 * u) * cos(u * 9.5f)
}

/** Per glyph, how many of its satin threads are laid at t (written into [out]). */
internal fun embGlyphPresent(t: Float, counts: IntArray, out: FloatArray) {
    val n = counts.size
    var total = 0
    for (c in counts) total += c
    var acc = 0
    for (i in 0 until n) {
        val c = counts[i].toFloat()
        val u0 = UNPICK_FROM + (UNPICK_TO - UNPICK_FROM) * i / n
        val u1 = UNPICK_FROM + (UNPICK_TO - UNPICK_FROM) * (i + 1) / n
        val s0 = STITCH_FROM + (STITCH_TO - STITCH_FROM) * acc / total
        val s1 = STITCH_FROM + (STITCH_TO - STITCH_FROM) * (acc + c) / total
        acc += counts[i]
        out[i] = when {
            t < u0 -> c
            t < s0 -> c * (1 - embEaseInOut(embSeg(t, u0, u1)))
            else -> c * embSeg(t, s0, s1)
        }
    }
}

/** The knot: scale and alpha, packed into [out]. */
internal fun embKnotState(t: Float, out: FloatArray) {
    if (t >= UNPICK_FROM && t < KNOT_FROM) {
        val o = embSeg(t, UNPICK_TO - 0.15f, UNPICK_TO)
        out[0] = 1 - 0.3f * o; out[1] = 1 - o
        return
    }
    val u = embSeg(t, KNOT_FROM, KNOT_TO)
    out[0] = if (t < UNPICK_FROM) 1f else embPop(u)
    out[1] = if (t < UNPICK_FROM) 1f else min(1f, u * 4)
}

/** The subheading's alpha. */
internal fun embSubAlpha(t: Float): Float {
    val o = embEaseInOut(embSeg(t, 1.2f, 1.6f))
    val back = embEaseOut(embSeg(t, 6.9f, 7.5f))
    return if (t < 6.9f) 1 - o else back
}

/** The subheading's rise in pt. */
internal fun embSubRise(t: Float): Float = if (t < 6.9f) 0f else (1 - embEaseOut(embSeg(t, 6.9f, 7.5f))) * 10

/** Whether the needle is out (1) or resting off the work (0). */
internal fun embNeedleOut(t: Float) = embEaseInOut(embSeg(t, 1.0f, 1.3f)) * (1 - embEaseInOut(embSeg(t, 7.0f, 7.5f)))
