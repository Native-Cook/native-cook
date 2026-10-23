package com.ngocdevv.nativecook.blocks.charts.rangemorphpriceline

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

// Every range has the same number of points, so a morph moves each point straight up or down.
internal const val POINTS = 120

/**
 * A range of the selector.
 *
 * @param change Percent change over the range; its sign colours the line.
 * @param series One value per point, oldest first; any scale, the chart fits it to its height.
 */
data class RangeMorphPriceLineRange(val label: String, val change: Double, val series: List<Double>)

private class Walk(
    val label: String,
    val seed: Int,
    val change: Double,
    /** How far the walk drifts against its noise: 0 is flat, 1 climbs about its own wobble. */
    val drift: Double,
    /** How jagged it is: the share of steps that jump instead of stepping. */
    val spikes: Double,
    /** Stretches where the price did not move (markets closed), as first and last points. */
    val holds: List<Pair<Int, Int>> = emptyList(),
    /** A hump in the first quarter, which makes the morph into this range seem to roll. */
    val bump: Double = 0.0,
)

/** mulberry32, bit for bit as in the React Native version: the same seed gives the same walk. */
private fun seeded(seed: Int): () -> Double {
    var a = seed
    return {
        a += 0x6d2b79f5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
    }
}

private fun walk(w: Walk): List<Double> {
    val random = seeded(w.seed)
    val raw = DoubleArray(POINTS)
    var value = 0.0
    for (i in 0 until POINTS) {
        val jump = if (random() < w.spikes) 4.0 else 1.0
        value += (random() - 0.5) * jump
        raw[i] = value
    }
    // Take the walk's own trend out, then put the range's in, so the sign of the change is honest.
    val first = raw[0]
    val last = raw[POINTS - 1]
    var spread = 0.0
    for (v in raw) spread = max(spread, abs(v - first))
    val out = DoubleArray(POINTS) { i ->
        val t = i / (POINTS - 1).toDouble()
        val hump = w.bump * exp(-((t - 0.16) / 0.07).pow(2))
        raw[i] - first - (last - first) * t + w.drift * spread * t + hump * spread
    }
    for ((from, to) in w.holds) for (i in from + 1..to) out[i] = out[from]
    return out.toList()
}

private val WALKS = listOf(
    Walk("1D", seed = 11, change = -0.18, drift = -0.5, spikes = 0.05),
    Walk("1W", seed = 7, change = 0.32, drift = 1.1, spikes = 0.12, holds = listOf(86 to 104, 106 to 116)),
    Walk("1M", seed = 23, change = -1.69, drift = -0.9, spikes = 0.18),
    Walk("3M", seed = 41, change = -0.65, drift = -0.6, spikes = 0.14, bump = 0.9),
    Walk("6M", seed = 5, change = 0.62, drift = 0.3, spikes = 0.16),
    Walk("1Y", seed = 63, change = 0.97, drift = 0.5, spikes = 0.2),
    Walk("2Y", seed = 87, change = 8.53, drift = 1.4, spikes = 0.2),
)

val RangeMorphPriceLineRanges: List<RangeMorphPriceLineRange> =
    WALKS.map { RangeMorphPriceLineRange(it.label, it.change, walk(it)) }

/** A series fitted to 0 (its lowest point) to 1 (its highest), with exactly [POINTS] values. */
internal fun normalized(series: List<Double>): FloatArray {
    val low = series.minOrNull() ?: 0.0
    val high = series.maxOrNull() ?: 0.0
    return FloatArray(POINTS) { i ->
        val at = if (series.size == POINTS) i else (i / (POINTS - 1).toDouble() * (series.size - 1)).roundToInt()
        if (high > low) ((series[at] - low) / (high - low)).toFloat() else 0.5f
    }
}
