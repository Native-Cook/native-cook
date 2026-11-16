package com.ngocdevv.nativecook.blocks.pickers.skytimescrub

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Sky tint (`sts-tint.ts`): what the sky looks like at 16 elevations so the UI can be lit by it without reading a pixel back. Rows:
 * elevation, zenith rgb, horizon rgb, sun rgb.
 */
internal object SkyTimeScrubTint {
    private val TINT = doubleArrayOf(
        -18.0, 27.0, 24.0, 47.0, 9.0, 8.0, 16.0, 224.0, 48.0, 26.0,
        -12.0, 28.0, 25.0, 50.0, 12.0, 11.0, 19.0, 224.0, 48.0, 26.0,
        -6.0, 19.0, 21.0, 45.0, 82.0, 57.0, 26.0, 224.0, 48.0, 26.0,
        -3.0, 28.0, 41.0, 57.0, 114.0, 73.0, 24.0, 224.0, 48.0, 26.0,
        -1.0, 31.0, 44.0, 54.0, 116.0, 64.0, 18.0, 224.0, 48.0, 26.0,
        0.0, 31.0, 43.0, 51.0, 115.0, 61.0, 16.0, 255.0, 106.0, 30.0,
        1.0, 33.0, 46.0, 54.0, 118.0, 63.0, 16.0, 255.0, 129.0, 41.0,
        2.0, 36.0, 51.0, 58.0, 122.0, 68.0, 16.0, 255.0, 171.0, 63.0,
        3.0, 37.0, 54.0, 59.0, 128.0, 77.0, 18.0, 255.0, 194.0, 74.0,
        4.5, 38.0, 54.0, 58.0, 137.0, 94.0, 22.0, 255.0, 220.0, 146.0,
        6.0, 38.0, 54.0, 58.0, 145.0, 115.0, 27.0, 255.0, 246.0, 218.0,
        10.0, 34.0, 48.0, 55.0, 153.0, 154.0, 40.0, 255.0, 246.0, 218.0,
        18.0, 22.0, 33.0, 55.0, 106.0, 141.0, 141.0, 255.0, 246.0, 218.0,
        30.0, 20.0, 32.0, 61.0, 106.0, 153.0, 177.0, 255.0, 246.0, 218.0,
        45.0, 21.0, 33.0, 64.0, 106.0, 154.0, 182.0, 255.0, 246.0, 218.0,
        62.0, 21.0, 34.0, 66.0, 105.0, 154.0, 183.0, 255.0, 246.0, 218.0,
    )
    private const val ROW = 10
    private const val COUNT = 16

    /** [zenith r,g,b, horizon r,g,b, sun r,g,b] in 0-255 for a sun elevation (clamped to -18..62), written into [out]. */
    fun at(elevation: Double, out: DoubleArray) {
        val first = TINT[0]
        val last = TINT[(COUNT - 1) * ROW]
        val e = min(last, max(first, elevation))
        var i = 0
        while (i < COUNT - 2 && e > TINT[(i + 1) * ROW]) i++
        val a = i * ROW
        val b = a + ROW
        val span = TINT[b] - TINT[a]
        val t = if (span > 0) (e - TINT[a]) / span else 0.0
        for (k in 1 until ROW) out[k - 1] = TINT[a + k] + (TINT[b + k] - TINT[a + k]) * t
    }

    fun luminance(r: Double, g: Double, b: Double) = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255

    fun argb(r: Double, g: Double, b: Double, alpha: Double = 1.0): Int {
        fun c(v: Double) = min(255, max(0, v.roundToInt()))
        return (c(alpha * 255) shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }
}

