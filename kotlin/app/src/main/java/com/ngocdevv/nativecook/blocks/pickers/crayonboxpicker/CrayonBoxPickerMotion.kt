package com.ngocdevv.nativecook.blocks.pickers.crayonboxpicker

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Crayon Box Picker — the autoPlay script. Every value is a pure function of the loop time t.

   0.0 – 1.2   rest: twelve crayons standing in their box, Sky is the colour, its swatch on the paper.
   1.2 – 2.4   Tangerine is pulled up against the box's friction, wobbles as it comes free, its
               neighbours lean into the gap.
   2.4 – 2.75  it swings down onto the paper (arc, turning to a writing angle, a little lift).
   2.75 – 3.75 it scribbles a swatch; the Sky swatch fades.
   3.75        Tangerine becomes the colour (the name rolls).
   3.8 – 4.6   it goes home: back up along the arc, then slides down into its slot (spring settle).
   4.8 – 8.0   the same for Sky.
   8.0 – 9.0   rest.
*/

internal const val LOOP = 9f

internal class CrayonInfo(val name: String, val hex: Long, val rgb: FloatArray)

internal val Crayons = listOf(
    CrayonInfo("Cherry", 0xD23A43, floatArrayOf(0.82f, 0.23f, 0.26f)),
    CrayonInfo("Tangerine", 0xF07A2A, floatArrayOf(0.94f, 0.48f, 0.16f)),
    CrayonInfo("Lemon", 0xF2C531, floatArrayOf(0.95f, 0.77f, 0.19f)),
    CrayonInfo("Lime", 0x86B83A, floatArrayOf(0.53f, 0.72f, 0.23f)),
    CrayonInfo("Moss", 0x4F7A3A, floatArrayOf(0.31f, 0.48f, 0.23f)),
    CrayonInfo("Ocean", 0x1F6F9E, floatArrayOf(0.12f, 0.44f, 0.62f)),
    CrayonInfo("Sky", 0x5BB2E6, floatArrayOf(0.36f, 0.7f, 0.9f)),
    CrayonInfo("Grape", 0x7A4FB0, floatArrayOf(0.48f, 0.31f, 0.69f)),
    CrayonInfo("Bubblegum", 0xF08BB6, floatArrayOf(0.94f, 0.55f, 0.71f)),
    CrayonInfo("Coral", 0xF2876E, floatArrayOf(0.95f, 0.53f, 0.43f)),
    CrayonInfo("Cocoa", 0x7A4A2C, floatArrayOf(0.48f, 0.29f, 0.17f)),
    CrayonInfo("Midnight", 0x2B3350, floatArrayOf(0.17f, 0.2f, 0.31f)),
)
internal const val TANGERINE = 1
internal const val SKY = 6
internal const val REST_COLOUR = SKY

/** One pick, as times: pull from, pull to, fly to paper end, scribble end, fly home end, settle end. */
internal class CrayonPick(val crayon: Int, val times: FloatArray)

internal val LoopPicks = listOf(
    CrayonPick(TANGERINE, floatArrayOf(1.2f, 2.4f, 2.75f, 3.75f, 4.25f, 4.6f)),
    CrayonPick(SKY, floatArrayOf(4.8f, 5.8f, 6.15f, 7.0f, 7.45f, 8.0f)),
)

internal fun crSeg(t: Float, a: Float, b: Float) = min(1f, max(0f, (t - a) / (b - a)))

internal fun crEaseInOut(u: Float) = if (u < 0.5f) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2

internal fun crEaseOut(u: Float) = 1 - (1 - u).pow(3)

/** A settle with one soft overshoot (spring-like), 0 → 1. */
internal fun crSettle(u: Float): Float {
    if (u <= 0) return 0f
    if (u >= 1) return 1f
    return 1 - exp(-6 * u) * cos(u * 8)
}

/**
 * Where the pick is at t, written into [out]: phase, amount, wobble. Phase 0 = in its slot, 1 =
 * pulling, 2 = flying to the paper, 3 = scribbling, 4 = flying home, 5 = settling into the slot.
 */
internal fun crPickPhase(times: FloatArray, t: Float, out: FloatArray) {
    val tt = times
    out[2] = 0f
    when {
        t < tt[0] || t >= tt[5] -> { out[0] = 0f; out[1] = 0f }
        t < tt[1] -> {
            // Friction: quick at first, then it drags; free after 60 % of the window, where it wobbles.
            val u = crSeg(t, tt[0], tt[0] + (tt[1] - tt[0]) * 0.6f)
            val free = crSeg(t, tt[0] + (tt[1] - tt[0]) * 0.6f, tt[1])
            out[0] = 1f
            out[1] = crEaseOut(u) * 0.97f + 0.03f * sin(u * 12) * (1 - u)
            out[2] = sin(free * PI.toFloat() * 3) * (1 - free) * (if (u >= 1) 1f else 0f)
        }
        t < tt[2] -> { out[0] = 2f; out[1] = crEaseInOut(crSeg(t, tt[1], tt[2])) }
        t < tt[3] -> { out[0] = 3f; out[1] = crSeg(t, tt[2], tt[3]) }
        t < tt[4] -> { out[0] = 4f; out[1] = crEaseInOut(crSeg(t, tt[3], tt[4])) }
        else -> { out[0] = 5f; out[1] = 1 - crSettle(crSeg(t, tt[4], tt[5])) }
    }
}

/** Swatches on the paper: old colour, old alpha, new colour, new progress 0..1. */
internal fun crLoopSwatch(t: Float, out: FloatArray) {
    val a = crSeg(t, 2.75f, 3.75f)
    val b = crSeg(t, 6.15f, 7.0f)
    when {
        t < 2.75f -> set4(out, SKY, 1f, SKY, 1f)
        t < 3.75f -> set4(out, SKY, 1 - a, TANGERINE, a)
        t < 6.15f -> set4(out, TANGERINE, 1f, TANGERINE, 1f)
        t < 7.0f -> set4(out, TANGERINE, 1 - b, SKY, b)
        else -> set4(out, SKY, 1f, SKY, 1f)
    }
}

private fun set4(o: FloatArray, a: Int, b: Float, c: Int, d: Float) { o[0] = a.toFloat(); o[1] = b; o[2] = c.toFloat(); o[3] = d }

/** The chosen colour's name: from, to, roll 0..1. */
internal fun crLoopColour(t: Float, out: FloatArray) {
    when {
        t < 3.75f -> { out[0] = TANGERINE.toFloat(); out[1] = SKY.toFloat(); out[2] = 1f }
        t < 7.0f -> { out[0] = SKY.toFloat(); out[1] = TANGERINE.toFloat(); out[2] = crEaseInOut(crSeg(t, 3.75f, 4.1f)) }
        else -> { out[0] = TANGERINE.toFloat(); out[1] = SKY.toFloat(); out[2] = crEaseInOut(crSeg(t, 7.0f, 7.35f)) }
    }
}

/** Which pick is under way at t and its phase: crayon, phase, amount, wobble. */
internal fun crLoopPick(t: Float, out: FloatArray, tmp: FloatArray) {
    for (p in LoopPicks) {
        crPickPhase(p.times, t, tmp)
        if (tmp[0] != 0f) { out[0] = p.crayon.toFloat(); out[1] = tmp[0]; out[2] = tmp[1]; out[3] = tmp[2]; return }
    }
    out[0] = -1f; out[1] = 0f; out[2] = 0f; out[3] = 0f
}
