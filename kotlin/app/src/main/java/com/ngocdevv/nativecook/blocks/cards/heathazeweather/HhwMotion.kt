// Heat Haze Weather — the loop, the forecast's numbers and the scripted finger, in closed form
// (hhw-motion.ts).
//
// Everything on screen is a function of one number, the hour H (12 … 21, a float). The film moves H
// with the hour slider; the scene shader (HhwScene.kt) reads the sun's place, the heat and the dusk
// derived from H here.
package com.ngocdevv.nativecook.blocks.cards.heathazeweather

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

const val LOOP = 10.0

/** the slider's range, hours (12 PM … 9 PM) */
internal const val H_MIN = 12.0
internal const val H_MAX = 21.0
/** the hour the screen rests at, the hottest hour the film visits, and the evening it ends on */
internal const val H_REST = 13.0
internal const val H_PEAK = 15.5
internal const val H_EVENING = 20.0

/** the loop's phases, seconds */
internal object T {
    /** the slider is dragged from 1 PM to 3:30 PM: the heat builds, the temperature counts up */
    const val TO_PEAK_A = 1.2
    const val TO_PEAK_B = 3.4
    /** the ghost finger rests on the road's mirage, adding a local shimmer */
    const val SHIMMER_A = 3.7
    const val SHIMMER_B = 4.75
    /** then dragged to 8 PM: the haze calms and the colours cool */
    const val TO_EVENING_A = 5.0
    const val TO_EVENING_B = 7.6
    /** and back to 1 PM */
    const val BACK_A = 8.3
    const val BACK_B = 9.5
}

/** the horizon, as a fraction of the screen's height */
internal const val HORIZON = 0.54f

/** the haze field */
internal object Haze {
    /** the haze's widest displacement at full heat, dp */
    const val AMP = 3.4f
    /** the band it lives in: its centre below the horizon and its e-fold, fractions of the height */
    const val BAND_OFFSET = 0.015f
    const val BAND_FOLD = 0.07f
    /** noise cell, dp (x, y): wide and flat, like rising air */
    const val CELL_X = 13f
    const val CELL_Y = 6f
    /** how far the field rises in one flow period, noise cells */
    const val RISE = 8f
    /** the flow repeats every FLOW seconds (LOOP is a whole multiple of it) */
    const val FLOW = 2.5
    /** the finger's own shimmer: extra displacement, dp, and reach (Gaussian), dp */
    const val TOUCH_AMP = 7f
    const val TOUCH_RADIUS = 74f
}

/** a touch hands the screen back this long after it ends, seconds */
internal const val IDLE = 2.0
/** then the hour eases back to rest over this long, seconds */
internal const val RETURN = 1.2
/** the finger's shimmer comes and goes at this rate, 1/s */
internal const val TOUCH_EASE = 9f
/** the slider's thumb, dp */
internal const val THUMB = 28f

data class HeatHazeForecast(
    /** whole hours from 12 to 21, in order */
    val hours: List<Int> = listOf(12, 13, 14, 15, 16, 17, 18, 19, 20, 21),
    /** °, one per hour */
    val temps: List<Double> = listOf(33.0, 36.0, 39.0, 41.0, 41.0, 39.0, 36.0, 33.0, 31.0, 29.0),
    /** UV index 0 … 11, one per hour */
    val uv: List<Double> = listOf(10.0, 10.0, 9.0, 8.0, 6.0, 4.0, 2.0, 1.0, 0.0, 0.0),
    /** sunset as an hour, e.g. 19.87 = 7:52 PM */
    val sunset: Double = 19.87,
)

internal fun clamp01(v: Double) = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v

/** 0 before a, 1 after b */
internal fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))

internal fun smooth(v: Double) = v * v * (3 - 2 * v)

internal fun easeInOutCubic(v: Double) = if (v < 0.5) 4 * v * v * v else 1 - (-2 * v + 2).pow(3) / 2

/** the film's hour at loop time t */
internal fun hourAt(t: Double): Double {
    val up = easeInOutCubic(seg(t, T.TO_PEAK_A, T.TO_PEAK_B))
    val eve = easeInOutCubic(seg(t, T.TO_EVENING_A, T.TO_EVENING_B))
    val back = easeInOutCubic(seg(t, T.BACK_A, T.BACK_B))
    return H_REST + (H_PEAK - H_REST) * up + (H_EVENING - H_PEAK) * eve - (H_EVENING - H_REST) * back
}

/** a forecast column read at hour h, linear between whole hours */
internal fun sample(values: List<Double>, h: Double): Double {
    val x = min(max(h - H_MIN, 0.0), (values.size - 1).toDouble())
    val i = min(floor(x).toInt(), values.size - 2)
    val f = x - i
    return values[i] + (values[i + 1] - values[i]) * f
}

/** how far into the evening the light is, 0 … 1 */
internal fun duskAt(h: Double, sunset: Double) = smooth(seg(h, sunset - 3.4, sunset + 0.6))

/** the haze's strength, 0 … 1: the heat of the air, calmed by the evening */
internal fun heatAt(temp: Double, dusk: Double) = clamp01((temp - 29) / 12).pow(1.2) * (1 - 0.8 * dusk)

/** the sun's place in dp: high in the early afternoon, on the hills at sunset */
internal fun sunAt(h: Double, sunset: Double, width: Float, height: Float, out: FloatArray) {
    val e = clamp01((sunset + 0.15 - h) / (sunset + 0.15 - 12.5))
    val lift = sin(e * PI / 2)
    val hy = height * HORIZON
    out[0] = (width * (0.72 - 0.34 * (h - H_MIN) / (H_MAX - H_MIN))).toFloat()
    out[1] = (hy - lift * (hy - height * 0.13)).toFloat()
}

/** a counter that rolls to the next whole number in the last part of each step */
internal fun rolled(v: Double, from: Double = 0.6): Double {
    val whole = floor(v)
    return whole + smooth(seg(v - whole, from, 1.0))
}

/** the ghost finger on the slider: how much shows, and how hard it presses */
internal fun sliderGhostAt(t: Double, out: DoubleArray) {
    var alpha = 0.0
    var press = 0.0
    for ((a, b) in DRAGS) {
        alpha += smooth(seg(t, a - 0.35, a - 0.12)) * (1 - smooth(seg(t, b + 0.05, b + 0.28)))
        press += smooth(seg(t, a - 0.15, a)) * (1 - smooth(seg(t, b, b + 0.15)))
    }
    out[0] = clamp01(alpha)
    out[1] = clamp01(press)
}

private val DRAGS = listOf(T.TO_PEAK_A to T.TO_PEAK_B, T.TO_EVENING_A to T.TO_EVENING_B, T.BACK_A to T.BACK_B)

/** the ghost finger on the road: x, y as fractions of the screen, how much shows, and its shimmer */
internal fun sceneGhostAt(t: Double, out: DoubleArray) {
    val a = T.SHIMMER_A
    val b = T.SHIMMER_B
    val u = smooth(seg(t, a + 0.15, b - 0.15))
    out[0] = 0.57 - 0.14 * u
    out[1] = HORIZON + 0.032
    out[2] = smooth(seg(t, a, a + 0.2)) * (1 - smooth(seg(t, b - 0.2, b)))
    out[3] = smooth(seg(t, a + 0.12, a + 0.4)) * (1 - smooth(seg(t, b - 0.35, b + 0.05)))
}

/** the UV index's band, 0 Low … 4 Extreme, crossing each boundary over a quarter of a point */
internal fun uvBandAt(uv: Double) =
    smooth(seg(uv, 2.25, 2.75)) + smooth(seg(uv, 5.25, 5.75)) + smooth(seg(uv, 7.25, 7.75)) + smooth(seg(uv, 10.25, 10.75))

/** a digit's place on its strip; the tens roll while the ones go from 9 to 10 */
internal fun stripOf(v: Double, tens: Boolean): Double {
    val ones = ((v % 10) + 10) % 10
    return if (tens) (floor(v / 10) % 10) + max(0.0, ones - 9) else ones
}

/** how much of condition i shows at hour h */
internal fun conditionAt(h: Double, i: Int): Double {
    val scorch = smooth(seg(h, 14.3, 14.9)) * (1 - smooth(seg(h, 16.6, 17.2)))
    val cool = smooth(seg(h, 16.6, 17.2))
    return when (i) {
        0 -> clamp01(1 - scorch - cool)
        1 -> scorch
        else -> cool
    }
}

/** the condition that shows most at hour h */
internal fun conditionOf(h: Double): Int {
    var best = 0
    for (i in 1 until 3) if (conditionAt(h, i) > conditionAt(h, best)) best = i
    return best
}

/** Reanimated's default withTiming curve */
internal fun easeInOutQuad(u: Double) = if (u < 0.5) 2 * u * u else 1 - (-2 * u + 2) * (-2 * u + 2) / 2

/** 19.87 → "7:52 PM" */
internal fun formatHour(h: Double): String {
    val whole = floor(h).toInt()
    val minutes = Math.round((h - whole) * 60).toInt()
    val hour12 = if (whole % 12 == 0) 12 else whole % 12
    return "$hour12:${minutes.toString().padStart(2, '0')} ${if (whole < 12) "AM" else "PM"}"
}
