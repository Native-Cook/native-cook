package com.ngocdevv.nativecook.blocks.inputs.dualrangehistogram

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

internal const val PRICE_MIN = 1000
internal const val PRICE_MAX = 5000
internal const val PRICE_STEP = 50
internal const val PRICE_GAP = 300
internal val PRICE_DEFAULT = 1800 to 3600
internal const val BIN_COUNT = 36

internal val PLACE_TYPES = listOf("Entire home", "Private room")
internal val BEDROOMS = listOf("Any", "1", "2", "3+")

internal class Amenity(val label: String, val share: Double)

internal val AMENITIES = listOf(
    Amenity("Pets allowed", 0.46),
    Amenity("Furnished", 0.62),
    Amenity("Balcony or garden", 0.38),
)

private class Shape(val peak: Double, val wide: Double, val total: Double)

// Where the prices of each kind of listing cluster, as shares of the axis.
private val SHAPES = listOf(
    listOf(Shape(0.34, 0.13, 940.0), Shape(0.26, 0.08, 410.0), Shape(0.38, 0.1, 460.0), Shape(0.62, 0.13, 310.0)),
    listOf(Shape(0.13, 0.08, 520.0), Shape(0.11, 0.06, 340.0), Shape(0.2, 0.07, 130.0), Shape(0.29, 0.07, 50.0)),
)

private fun hash(i: Int): Double {
    val x = sin(i * 12.9898 + 4.1) * 43758.5453
    return x - floor(x)
}

/** A believable, slightly ragged price distribution: a main hump, a smaller expensive one and a seeded jitter. */
internal fun makeBins(type: Int, bedrooms: Int, share: Double): FloatArray {
    val s = SHAPES[type][bedrooms]
    val raw = DoubleArray(BIN_COUNT) { i ->
        val x = (i + 0.5) / BIN_COUNT
        val main = exp(-((x - s.peak) / s.wide).pow(2))
        val tail = 0.34 * exp(-((x - (s.peak + 0.3)) / (s.wide * 1.5)).pow(2))
        val jitter = 0.72 + 0.56 * hash(i + type * 40 + bedrooms * 7)
        (main + tail + 0.025) * jitter
    }
    val sum = raw.sum()
    return FloatArray(BIN_COUNT) { (raw[it] / sum * s.total * share).toFloat() }
}
