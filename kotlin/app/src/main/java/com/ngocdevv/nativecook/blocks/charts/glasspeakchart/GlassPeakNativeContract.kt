package com.ngocdevv.nativecook.blocks.charts.glasspeakchart

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt

internal data class GlassPeakNativePeriod(
    val value: Double,
    val level: Double,
    val knots: Array<DoubleArray>,
    val nodes: DoubleArray,
)

/** The source contract is evaluated in doubles, matching JavaScript's number type. */
internal object GlassPeakNativeContract {
    const val samples = 96
    const val loop = 13.0
    const val peakNode = 2
    const val orbRadius = 11.0
    const val plotLeft = 28.0
    const val plotRight = 374.0
    const val plotTop = 294.0
    const val plotZero = 476.0

    val periods = arrayOf(
        GlassPeakNativePeriod(300.0, 1.0, arrayOf(
            doubleArrayOf(0.0, .074), doubleArrayOf(.103, .153), doubleArrayOf(.21, .09),
            doubleArrayOf(.318, .049), doubleArrayOf(.38, .14), doubleArrayOf(.418, .47),
            doubleArrayOf(.45, .87), doubleArrayOf(.488, .985), doubleArrayOf(.526, 1.0),
            doubleArrayOf(.571, .73), doubleArrayOf(.626, .4), doubleArrayOf(.692, .206),
            doubleArrayOf(.733, .153), doubleArrayOf(.828, .115), doubleArrayOf(1.0, .13),
        ), doubleArrayOf(.103, .318, .526, .733)),
        GlassPeakNativePeriod(48.0, .42, arrayOf(
            doubleArrayOf(0.0, .13), doubleArrayOf(.14, .23), doubleArrayOf(.3, .1),
            doubleArrayOf(.44, .2), doubleArrayOf(.56, .4), doubleArrayOf(.62, .455),
            doubleArrayOf(.68, .47), doubleArrayOf(.74, .44), doubleArrayOf(.8, .33),
            doubleArrayOf(.88, .2), doubleArrayOf(1.0, .27),
        ), doubleArrayOf(.14, .3, .68, .88)),
        GlassPeakNativePeriod(124.0, .68, arrayOf(
            doubleArrayOf(0.0, .05), doubleArrayOf(.16, .1), doubleArrayOf(.28, .07),
            doubleArrayOf(.42, .22), doubleArrayOf(.54, .3), doubleArrayOf(.64, .34),
            doubleArrayOf(.72, .56), doubleArrayOf(.77, .69), doubleArrayOf(.8, .71),
            doubleArrayOf(.84, .67), doubleArrayOf(.94, .46), doubleArrayOf(1.0, .5),
        ), doubleArrayOf(.16, .42, .8, .94)),
    )

    val curves = periods.map { buildYs(it.knots) }.toTypedArray()

    fun buildYs(knots: Array<DoubleArray>): DoubleArray {
        val n = knots.size
        val slopes = DoubleArray(n - 1)
        val tangents = DoubleArray(n)
        for (i in 0 until n - 1) {
            slopes[i] = (knots[i + 1][1] - knots[i][1]) / (knots[i + 1][0] - knots[i][0])
        }
        tangents[0] = slopes[0]
        for (i in 1 until n - 1) {
            tangents[i] = if (slopes[i - 1] * slopes[i] <= 0.0) 0.0 else (slopes[i - 1] + slopes[i]) / 2.0
        }
        tangents[n - 1] = slopes[n - 2]
        for (i in 0 until n - 1) {
            if (slopes[i] == 0.0) {
                tangents[i] = 0.0
                tangents[i + 1] = 0.0
            } else {
                val a = tangents[i] / slopes[i]
                val b = tangents[i + 1] / slopes[i]
                val sum = a * a + b * b
                if (sum > 9.0) {
                    val scale = 3.0 / sqrt(sum)
                    tangents[i] = scale * a * slopes[i]
                    tangents[i + 1] = scale * b * slopes[i]
                }
            }
        }
        val ys = DoubleArray(samples)
        var knot = 0
        for (sample in 0 until samples) {
            val u = sample.toDouble() / (samples - 1)
            while (knot < n - 2 && u > knots[knot + 1][0]) knot++
            val width = knots[knot + 1][0] - knots[knot][0]
            val t = (u - knots[knot][0]) / width
            val t2 = t * t
            val t3 = t2 * t
            ys[sample] =
                (2 * t3 - 3 * t2 + 1) * knots[knot][1] +
                    (t3 - 2 * t2 + t) * width * tangents[knot] +
                    (-2 * t3 + 3 * t2) * knots[knot + 1][1] +
                    (t3 - t2) * width * tangents[knot + 1]
        }
        repeat(4) {
            var previous = ys[0]
            for (sample in 1 until samples - 1) {
                val here = ys[sample]
                ys[sample] = (previous + 2 * here + ys[sample + 1]) / 4
                previous = here
            }
        }
        return ys
    }

    fun heightAt(ys: DoubleArray, u: Double): Double {
        val position = u.coerceIn(0.0, 1.0) * (samples - 1)
        val index = minOf(samples - 2, floor(position).toInt())
        return ys[index] + (ys[index + 1] - ys[index]) * (position - index)
    }

    fun xOf(u: Double) = plotLeft + u * (plotRight - plotLeft)
    fun yOf(value: Double) = plotZero - value * (plotZero - plotTop)

    fun poolSurfaceY(level: Double, hump: Double, humpX: Double, slope: Double, x: Double): Double {
        val dx = x - humpX
        val front = 656.0 - (10 + 140 * level) - hump * 84 * exp(-(dx * dx) / (2 * 58 * 58)) - slope * (x - 201) * .4
        return 656.0 - .42 * (656.0 - front)
    }

    fun dropBottom(x: Double, peak: Boolean): Double {
        val distance = abs(x - 201.0)
        return if (distance < 64.0) 492.0 else if (peak || distance < 100.0) 516.0 else 556.0
    }
}
