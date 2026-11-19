package com.ngocdevv.nativecook.blocks.cards.meshgradientweather

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Soft Weather: the forecast, the sky palettes and the film, as pure functions. Everything the screen shows is a
 * function of `hour` (a fractional index into [hours], what the hourly row is scrolled to) and the ambient clock.
 * A port of the React Native `weatherMotion.ts` (and of the Swift `MeshGradientWeatherMotion`).
 */
internal object MeshGradientWeatherMotion {
    const val LOOP = 16.0

    enum class Sym { Sunrise, Sun, CloudSun, Cloud, Drizzle, Rain, Sunset, CloudMoon, MoonStars, Moon, CloudSunRain }

    class Hour(val label: String, val temp: Double, val symbol: Sym, val cond: Int, val rain: Double, val wind: Double)

    val conditions = listOf("Clear", "Sunny", "Partly Cloudy", "Cloudy", "Light Rain", "Rain")

    val hours = listOf(
        Hour("Now", 13.0, Sym.Sunrise, 0, 0.0, 6.0),
        Hour("8 AM", 14.0, Sym.Sun, 1, 0.0, 7.0),
        Hour("9 AM", 16.0, Sym.Sun, 1, 0.0, 8.0),
        Hour("10 AM", 18.0, Sym.Sun, 1, 0.0, 9.0),
        Hour("11 AM", 19.0, Sym.CloudSun, 2, 5.0, 11.0),
        Hour("12 PM", 20.0, Sym.CloudSun, 2, 10.0, 12.0),
        Hour("1 PM", 19.0, Sym.Cloud, 3, 30.0, 15.0),
        Hour("2 PM", 17.0, Sym.Drizzle, 4, 70.0, 18.0),
        Hour("3 PM", 16.0, Sym.Rain, 5, 90.0, 21.0),
        Hour("4 PM", 16.0, Sym.Rain, 5, 80.0, 19.0),
        Hour("5 PM", 16.0, Sym.Drizzle, 4, 40.0, 14.0),
        Hour("6 PM", 16.0, Sym.CloudSun, 2, 10.0, 11.0),
        Hour("7 PM", 15.0, Sym.Sunset, 0, 0.0, 9.0),
        Hour("8 PM", 14.0, Sym.CloudMoon, 2, 0.0, 8.0),
        Hour("9 PM", 13.0, Sym.MoonStars, 0, 0.0, 7.0),
        Hour("10 PM", 12.0, Sym.MoonStars, 0, 0.0, 6.0),
        Hour("11 PM", 12.0, Sym.Moon, 0, 0.0, 6.0),
        Hour("12 AM", 11.0, Sym.Moon, 0, 0.0, 5.0),
    )
    val lastHour = (hours.size - 1).toDouble()
    val temps = DoubleArray(hours.size) { hours[it].temp }
    val rainPct = DoubleArray(hours.size) { hours[it].rain }
    val wind = DoubleArray(hours.size) { hours[it].wind }

    // ---------------------------------------------------------------- colour

    private fun hexToLab(hex: Int): DoubleArray {
        fun lin(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = lin((hex shr 16) and 255)
        val g = lin((hex shr 8) and 255)
        val b = lin(hex and 255)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
        )
    }

    private fun palette(vararg rows: Int): DoubleArray {
        val out = DoubleArray(rows.size * 3)
        rows.forEachIndexed { i, hex -> hexToLab(hex).copyInto(out, i * 3) }
        return out
    }

    // Six skies, each a 4 x 4 grid read row by row from the top of the screen.
    private val dawn = palette(
        0x7C86C8, 0x8D8BCB, 0xA592C8, 0xB79BC6,
        0xA49ACB, 0xD6A3BE, 0xEBAFAE, 0xD9A6B9,
        0xE9B6AA, 0xF4C3A6, 0xF6C59F, 0xEDB7A8,
        0xF7D3B6, 0xFADBBD, 0xF5CFB5, 0xEEC6B8,
    )
    private val day = palette(
        0x5E97DA, 0x6FA3DE, 0x93B9E0, 0xE9CF9E,
        0x73A7E0, 0x8BB6E6, 0xA9C8E9, 0xDCD3B8,
        0x9CC0E8, 0xB1CDEC, 0xC3D7EE, 0xCCDBEA,
        0xBFD6EE, 0xCBDDF0, 0xD4E2F0, 0xD9E4EE,
    )
    private val overcast = palette(
        0x6F7F99, 0x7A89A0, 0x8693A8, 0x929CAE,
        0x8291A8, 0x8E9BB0, 0x9AA5B6, 0xA2ABBA,
        0x9AA6B8, 0xA6B0BF, 0xAEB6C3, 0xB3BAC5,
        0xAEB7C3, 0xB6BEC8, 0xBBC2CB, 0xBEC4CC,
    )
    private val rainy = palette(
        0x4B586E, 0x536077, 0x5B677D, 0x636D82,
        0x5A677E, 0x647189, 0x6C788E, 0x6A7489,
        0x6E7B91, 0x78849A, 0x7F8A9D, 0x7A8598,
        0x7D889B, 0x8590A1, 0x8A94A4, 0x86909F,
    )
    private val dusk = palette(
        0x4F4E8E, 0x5E5396, 0x705797, 0x7F5B95,
        0x7C5F9D, 0xB06E93, 0xC77D88, 0xA06E94,
        0xD38C83, 0xE2A07F, 0xE6A87B, 0xD99383,
        0xEAB585, 0xEEBF8C, 0xEAB185, 0xE0A388,
    )
    private val night = palette(
        0x161B3E, 0x1A2048, 0x202552, 0x262551,
        0x1E2854, 0x283669, 0x30356A, 0x2A2A5A,
        0x263563, 0x30447A, 0x38467A, 0x2F3769,
        0x212E57, 0x283962, 0x2E3D69, 0x262F59,
    )

    /** Sky keyframes on the hour index. Between two, an eased blend in OKLab. */
    private val skyH = doubleArrayOf(0.0, 3.0, 5.0, 6.5, 8.0, 9.2, 10.6, 12.0, 14.0)
    private val skyP = listOf(dawn, day, day, overcast, rainy, rainy, overcast, dusk, night)

    /** Rain on the glass, by hour. */
    private val rainH = doubleArrayOf(6.0, 7.0, 8.0, 9.3, 10.3, 11.0)
    private val rainV = doubleArrayOf(0.0, 0.45, 1.0, 1.0, 0.35, 0.0)

    // ---------------------------------------------------------------- easing

    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun smooth(x: Double) = x * x * (3 - 2 * x)

    /** OKLab blend that keeps the chroma, so blends never pass through grey. Writes into [out]. */
    private fun mixLab(a: DoubleArray, b: DoubleArray, f: Double, out: FloatArray, at: Int) {
        for (i in 0 until a.size / 3) {
            val k = i * 3
            val ca = sqrt(a[k + 1] * a[k + 1] + a[k + 2] * a[k + 2])
            val cb = sqrt(b[k + 1] * b[k + 1] + b[k + 2] * b[k + 2])
            val l = a[k] + (b[k] - a[k]) * f
            val x = a[k + 1] + (b[k + 1] - a[k + 1]) * f
            val y = a[k + 2] + (b[k + 2] - a[k + 2]) * f
            val c = sqrt(x * x + y * y)
            val s = if (c > 0.0001) (ca + (cb - ca) * f) / c else 1.0
            out[at + k] = l.toFloat(); out[at + k + 1] = (x * s).toFloat(); out[at + k + 2] = (y * s).toFloat()
        }
    }

    /** Linear lookup in a table of fractional-index values. */
    fun sample(table: DoubleArray, h: Double): Double {
        val n = table.size - 1
        val x = h.coerceIn(0.0, n.toDouble())
        val i = floor(x).toInt()
        if (i >= n) return table[n]
        return table[i] + (table[i + 1] - table[i]) * (x - i)
    }

    private fun keyed(xs: DoubleArray, ys: DoubleArray, x: Double): Double {
        if (x <= xs[0]) return ys[0]
        val n = xs.size - 1
        if (x >= xs[n]) return ys[n]
        var i = 0
        while (i < n - 1 && x > xs[i + 1]) i++
        return ys[i] + (ys[i + 1] - ys[i]) * smooth((x - xs[i]) / (xs[i + 1] - xs[i]))
    }

    fun rain(h: Double) = keyed(rainH, rainV, h)

    /** The sky's 48 OKLab numbers at hour h, written into [out] from [at]. */
    fun sky(h: Double, out: FloatArray, at: Int = 0) {
        val n = skyH.size - 1
        var i = 0
        if (h >= skyH[n]) i = n - 1 else while (i < n - 1 && h > skyH[i + 1]) i++
        val span = skyH[i + 1] - skyH[i]
        val f = if (h <= skyH[0]) 0.0 else if (h >= skyH[n]) 1.0 else smooth(clamp01((h - skyH[i]) / span))
        mixLab(skyP[i], skyP[i + 1], f, out, at)
    }

    /** Control-point offsets (32 numbers, fractions of the screen) at hour h and ambient time amb. */
    fun offsets(h: Double, amb: Double, rain: Double, out: FloatArray, at: Int = 0) {
        val tau = amb / LOOP * PI * 2
        val sun = if (h < 3) h / 3 else if (h < 6) 1.0 else if (h < 8) 1 - (h - 6) / 2 else 0.0
        for (j in 0 until 4) for (i in 0 until 4) {
            val k = (j * 4 + i) * 2
            val edgeX = i == 0 || i == 3
            val edgeY = j == 0 || j == 3
            val fi = i.toDouble()
            val fj = j.toDouble()
            val s = (i * 7 + j * 13) * 0.37
            var dx = 0.05 * sin(tau * (1 + ((i + j) and 1)) + s) + 0.055 * sin(h * 0.75 + fi * 1.3 + fj * 0.6)
            var dy = 0.03 * cos(tau * (1 + (i and 1)) + s * 1.7) + 0.035 * cos(h * 0.6 + fj * 1.7 + fi * 0.4)
            dx += -0.07 * sun * (fi / 3) * (1 - fj / 3)
            dy += 0.05 * sun * (fi / 3) * (1 - fj / 3) + 0.04 * rain * (if (edgeY) 0 else 1)
            if (edgeX) dx = 0.0
            if (edgeY) dy = 0.0
            out[at + k] = dx.toFloat(); out[at + k + 1] = dy.toFloat()
        }
    }

    // ---------------------------------------------------------------- the film

    fun filmHour(t: Double): Double = when {
        t < 2.0 -> 0.0
        t < 4.6 -> 4 * easeInOut(seg(t, 2.0, 4.6))
        t < 5.6 -> 4 + smooth(seg(t, 4.6, 5.6))
        t < 7.6 -> 5 + 3 * easeInOut(seg(t, 5.6, 7.6))
        t < 8.8 -> 8 + smooth(seg(t, 7.6, 8.8))
        t < 13.0 -> 9 + 6 * easeInOut(seg(t, 8.8, 11.2))
        else -> 15 * (1 - easeInOut(seg(t, 13.0, 14.6)))
    }

    /** Rain is kept off the glass while the row rewinds to Now. */
    fun filmRainGate(t: Double): Double =
        1 - smooth(seg(t, 13.0 - 0.2, 13.0 + 0.1)) * (1 - smooth(seg(t, 14.6 - 0.1, 14.6 + 0.2)))

    /** Weight of condition label c at hour h: a tent around each hour that has it. */
    fun condWeight(c: Int, h: Double): Double {
        var w = 0.0
        hours.forEachIndexed { i, hr ->
            if (hr.cond == c) {
                val d = abs(h - i)
                if (d < 1) w += 1 - d
            }
        }
        return smooth(clamp01(w))
    }
}
