package com.ngocdevv.nativecook.blocks.charts.tidegaugechart

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Tide Gauge Chart — the numbers and pure functions shared by the screen, the shader and the buoy
 (a port of tideGaugeMotion.ts). Everything the loop shows is a function of the loop time t:

   0.0 – 1.6   rest: the buoy floats at "now" (10:40), ripples travel along the surface.
   1.6 – 2.5   scrub back to 06:00 (ease-in-out); the water leans against the move.
   2.5 – 5.0   scrub forward through the high tide to 18:00; the surface sloshes at the turn.
   5.0 – 5.5   back to the high tide at 14:12 (ease-in-out), the slosh rings out.
   5.5 – 6.6   hold on the high tide: the reading glows.
   6.6 – 8.4   return to now (ease-in-out).
   8.4 – 9.0   rest; the last ripple of the slosh is faded out by 8.6 – 9.0 so t = 9 equals t = 0.
*/

internal const val LOOP = 9f

/** The chart spans one day, in hours. */
internal const val TIDE_DAY = 24f
/** "Now" on the chart: 10:40. */
internal const val TIDE_NOW = 10f + 40f / 60f
/** Water levels the plot shows, in metres (bottom and top of the plot). */
internal const val TIDE_H_MIN = -0.4f
internal const val TIDE_H_MAX = 3.0f

/** A semi-diurnal tide: high 2.4 m at 14:12, low 0.3 m at 08:05 (half a period earlier). */
private const val TIDE_MEAN = 1.35f
private const val TIDE_AMP = 1.05f
internal const val TIDE_HIGH_AT = 14.2f
internal const val TIDE_PERIOD = 12.23f

/** Extremes of the day (hours), where scrubbing past gives a selection tick. */
internal val TIDE_EXTREMES = floatArrayOf(TIDE_HIGH_AT - TIDE_PERIOD, TIDE_HIGH_AT - TIDE_PERIOD / 2, TIDE_HIGH_AT, TIDE_HIGH_AT + TIDE_PERIOD / 2)

internal const val TIDE_SUNRISE = 6f + 12f / 60f
internal const val TIDE_SUNSET = 18f + 47f / 60f

/** Slosh: a damped oscillator (rad/s, damping ratio) driven by the scrub speed (dp of tilt per dp/s). */
internal const val SLOSH_OMEGA = 9f
internal const val SLOSH_ZETA = 0.3f
internal const val SLOSH_GAIN = 0.018f
internal const val SLOSH_MAX = 14f

/** After a touch: wait, then ease back to the loop's rest (s). */
internal const val TIDE_IDLE_BEFORE_RETURN = 2f
internal const val TIDE_RETURN_FOR = 0.8f

internal class TidePlot(val x: Float, val y: Float, val w: Float, val h: Float)

internal fun tClamp(v: Float, lo: Float, hi: Float) = min(hi, max(lo, v))

private fun seg(t: Float, a: Float, b: Float) = tClamp((t - a) / (b - a), 0f, 1f)

internal fun tEaseInOut(x: Float): Float = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

private fun easeInOutSine(x: Float): Float = -(cos(PI.toFloat() * x) - 1) / 2

internal fun tide(hour: Float): Float = TIDE_MEAN + TIDE_AMP * cos(2 * PI.toFloat() * (hour - TIDE_HIGH_AT) / TIDE_PERIOD)

/** Rising (+1) or falling (-1) at an hour. */
internal fun tideTrend(hour: Float): Int = if (sin(2 * PI.toFloat() * (hour - TIDE_HIGH_AT) / TIDE_PERIOD) < 0f) 1 else -1

/** The scrubbed hour of the loop at t. */
internal fun tideScriptHour(t: Float): Float = when {
    t < 1.6f -> TIDE_NOW
    t < 2.5f -> TIDE_NOW + (6 - TIDE_NOW) * tEaseInOut(seg(t, 1.6f, 2.5f))
    t < 5.0f -> 6 + 12 * easeInOutSine(seg(t, 2.5f, 5.0f))
    t < 5.5f -> 18 + (TIDE_HIGH_AT - 18) * tEaseInOut(seg(t, 5.0f, 5.5f))
    t < 6.6f -> TIDE_HIGH_AT
    t < 8.4f -> TIDE_HIGH_AT + (TIDE_NOW - TIDE_HIGH_AT) * tEaseInOut(seg(t, 6.6f, 8.4f))
    else -> TIDE_NOW
}

/** The glow on the reading while the loop holds on the high tide. */
internal fun tideScriptGlow(t: Float): Float = easeInOutSine(seg(t, 5.3f, 5.8f)) * (1 - easeInOutSine(seg(t, 6.3f, 6.9f)))

/**
 * The loop's slosh at t into [out]: tilt dp, chop 0..1. Integrated from t = 0 with a fixed step
 * every frame, so it is a pure function of t; the tail is faded out before the loop closes.
 */
internal fun tideScriptSlosh(t: Float, plotW: Float, out: FloatArray) {
    val step = 1f / 60f
    val pxPerHour = plotW / TIDE_DAY
    var tilt = 0f
    var v = 0f
    var prev = tideScriptHour(0f)
    var speed = 0f
    val n = floor(t / step).toInt()
    for (i in 1..n) {
        val h = tideScriptHour(i * step)
        speed = (h - prev) / step * pxPerHour
        prev = h
        val drive = tClamp(-speed * SLOSH_GAIN, -SLOSH_MAX, SLOSH_MAX)
        val a = -SLOSH_OMEGA * SLOSH_OMEGA * (tilt - drive) - 2 * SLOSH_ZETA * SLOSH_OMEGA * v
        v += a * step
        tilt += v * step
    }
    val fade = 1 - easeInOutSine(seg(t, 8.5f, 9.0f))
    out[0] = tilt * fade
    out[1] = tClamp(abs(speed) / 700, 0f, 1f) * fade
}

internal fun hourToX(hour: Float, plot: TidePlot) = plot.x + hour / TIDE_DAY * plot.w

internal fun levelToY(h: Float, plot: TidePlot) = plot.y + (1 - (h - TIDE_H_MIN) / (TIDE_H_MAX - TIDE_H_MIN)) * plot.h

/**
 * The water surface at card x, exactly as the shader draws it: the tide curve, three travelling
 * ripples (whole numbers of cycles per loop), and the slosh tilt about the scrubber.
 */
internal fun surfaceY(x: Float, plot: TidePlot, phase: Float, scrubX: Float, tilt: Float, chop: Float): Float {
    val hour = (x - plot.x) / plot.w * TIDE_DAY
    var y = levelToY(tide(hour), plot)
    val T = 2 * PI.toFloat() * phase
    val amp = 1 + 1.6f * chop
    y += amp * (1.1f * sin(x * 0.071f - 3 * T) + 0.7f * sin(x * 0.13f + 5 * T + 1.3f) + 0.4f * sin(x * 0.29f - 8 * T + 2.1f))
    val d = tClamp((x - scrubX) / (plot.w * 0.5f), -1f, 1f)
    y += tilt * d
    return y
}

/** "14:12" for an hour of the day. */
internal fun clockText(hour: Float): String {
    val m = (tClamp(hour, 0f, TIDE_DAY - 1f / 60f) * 60).roundToInt()
    val hh = m / 60
    val mm = m % 60
    return "${if (hh < 10) "0" else ""}$hh:${if (mm < 10) "0" else ""}$mm"
}
