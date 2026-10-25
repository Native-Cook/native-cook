package com.ngocdevv.nativecook.blocks.pickers.tapemeasurepicker

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 The tape's value (cm under the red hairline) and its wobble (same maths as tapeMeasureMotion.ts).

 autoPlay (LOOP = 8 s). The value is 178 plus a sum of spring steps, each a closed-form damped
 response that starts at rest, so the value and its speed are pure functions of t:
   0.0 – 1.3   rest at 178 cm.
   1.3 – 1.42  anticipation: the tape slips back 0.5 cm.
   1.42 – 3.8  pulled out to 192 (ω 6, ζ 0.72), wobbling while it moves.
   3.8 – 5.4   let go: it retracts to 165 with a whip (ω 11, ζ 0.6).
   6.6 – 7.6   pulled back to 178 (ω 7.5, ζ 0.8).
   7.6 – 8.0   rest (the last residual is faded out so t = 8 is exactly t = 0).

 Wobble: a travelling wave along the tape (3 Hz), its height growing toward the free end; its
 amplitude follows the tape's recent speed (a short decaying memory).
 */

const val LOOP = 8.0
internal const val TAPE_REST = 178.0
internal const val TAPE_MIN = 120.0
internal const val TAPE_MAX = 230.0
/** Dp per centimetre on screen. */
internal const val TAPE_PPCM = 20f
internal const val TAPE_WAVE_HZ = 3.0
internal const val TAPE_WAVE_LENGTH = 170f
private const val WAVE_MAX = 6.0

/** [start, change, ω, ζ] */
private val Steps = arrayOf(
    doubleArrayOf(1.3, -0.5, 18.0, 0.9),
    doubleArrayOf(1.42, 14.5, 6.0, 0.72),
    doubleArrayOf(3.8, -27.0, 11.0, 0.6),
    doubleArrayOf(6.6, 13.0, 7.5, 0.8),
)

internal fun tapeSeg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)

internal fun tapeEaseInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

/** The loop's value (cm) at loop time t; its speed (cm/s) goes into [speed][0]. */
internal fun tapeLoopValue(t: Double, speed: DoubleArray? = null): Double {
    var v = TAPE_REST
    var sp = 0.0
    for (st in Steps) {
        val tau = t - st[0]
        if (tau <= 0) continue
        val w = st[2]
        val z = st[3]
        val wd = w * sqrt(1 - z * z)
        val e = exp(-z * w * tau)
        val c = cos(wd * tau)
        val s = sin(wd * tau)
        v += st[1] * (1 - e * (c + (z * w / wd) * s))
        sp += st[1] * e * (w * w / wd) * s
    }
    val fade = 1 - tapeSeg(t, 7.6, 7.95)
    speed?.set(0, sp * fade)
    return TAPE_REST + (v - TAPE_REST) * fade
}

internal fun tapeWobbleFromSpeed(speed: Double) = WAVE_MAX * (1 - exp(-speed / 70))

/** The wobble's amplitude: the strongest recent speed, remembered with a 0.3 s decay. */
internal fun tapeLoopWobble(t: Double, scratch: DoubleArray): Double {
    var s = 0.0
    for (j in 0 until 7) {
        val tau = j * 0.06
        tapeLoopValue(max(0.0, t - tau), scratch)
        val sp = abs(scratch[0]) * exp(-tau / 0.3)
        if (sp > s) s = sp
    }
    return tapeWobbleFromSpeed(s)
}

/** Rubber band past the ends of the scale (cm). */
internal fun tapeBand(v: Double): Double {
    if (v > TAPE_MAX) {
        val o = v - TAPE_MAX
        return TAPE_MAX + o / (1 + o / 8)
    }
    if (v < TAPE_MIN) {
        val o = TAPE_MIN - v
        return TAPE_MIN - o / (1 + o / 8)
    }
    return v
}

/** The inverse of [tapeBand], so a drag that starts past an end keeps its place. */
internal fun tapeUnband(v: Double): Double {
    if (v > TAPE_MAX) {
        val o = min(v - TAPE_MAX, 7.9)
        return TAPE_MAX + o / (1 - o / 8)
    }
    if (v < TAPE_MIN) {
        val o = min(TAPE_MIN - v, 7.9)
        return TAPE_MIN - o / (1 - o / 8)
    }
    return v
}

/** A height in feet and inches, e.g. "5 ft 10 in". */
internal fun tapeFeetInches(cm: Int): String {
    val inches = (cm / 2.54).roundToInt()
    return "${floor(inches / 12.0).toInt()} ft ${inches % 12} in"
}
