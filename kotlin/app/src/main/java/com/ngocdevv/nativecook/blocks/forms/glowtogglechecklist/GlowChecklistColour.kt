package com.ngocdevv.nativecook.blocks.forms.glowtogglechecklist

import kotlin.math.pow

/** OKLab interpolation in the source's sRGB working space. */
internal object GlowChecklistColour {
    private val hues = arrayOf(
        doubleArrayOf(1.0, 0.231, 0.231), doubleArrayOf(0.91, 0.19, 0.55),
        doubleArrayOf(0.47, 0.33, 1.0), doubleArrayOf(0.15, 0.745, 0.705),
        doubleArrayOf(0.18, 0.878, 0.416),
    )
    private fun linear(n: Double) = if (n <= 0.04045) n / 12.92 else ((n + 0.055) / 1.055).pow(2.4)
    private fun srgb(n: Double) = (if (n <= 0.0031308) n * 12.92 else 1.055 * n.coerceAtLeast(0.0).pow(1 / 2.4) - 0.055).coerceIn(0.0, 1.0)
    private fun lab(rgb: DoubleArray): DoubleArray {
        val r = linear(rgb[0])
        val g = linear(rgb[1])
        val b = linear(rgb[2])
        val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s)
    }
    private fun fromLab(lab: DoubleArray): DoubleArray {
        val l = lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2]
        val m = lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2]
        val s = lab[0] - 0.0894841775 * lab[1] - 1.291485548 * lab[2]
        return doubleArrayOf(
            srgb(4.0767416621 * l*l*l - 3.3077115913 * m*m*m + 0.2309699292 * s*s*s),
            srgb(-1.2684380046 * l*l*l + 2.6097574011 * m*m*m - 0.3413193965 * s*s*s),
            srgb(-0.0041960863 * l*l*l - 0.7034186147 * m*m*m + 1.707614701 * s*s*s))
    }
    private fun between(a: DoubleArray, b: DoubleArray, t: Double): DoubleArray {
        val left = lab(a)
        val right = lab(b)
        return fromLab(DoubleArray(3) { left[it] + (right[it] - left[it]) * t })
    }
    fun at(hue: Double, tip: Double): FloatArray {
        val h = hue.coerceIn(0.0, 3.0)
        val i = minOf(2, h.toInt())
        val rgb = between(between(hues[i], hues[i + 1], h - i), hues[4], tip.coerceIn(0.0, 1.0))
        return FloatArray(3) { rgb[it].toFloat() }
    }
}
