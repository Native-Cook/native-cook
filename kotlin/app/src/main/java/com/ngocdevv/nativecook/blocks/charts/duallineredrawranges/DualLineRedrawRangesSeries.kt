package com.ngocdevv.nativecook.blocks.charts.duallineredrawranges

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

data class LineRange(val key: String, val name: String, val caption: String, val ticks: List<String>, val high: List<Double>, val low: List<Double>)

// Int overflow and unsigned shifts preserve the source's mulberry32 bit pattern.
fun walk(seed: Int, count: Int, start: Double, drift: Double, step: Double, lo: Double, hi: Double): List<Double> {
    var a = seed; var v = start
    return List(count) {
        val value = floor(v * 10 + 0.5) / 10
        a += 0x6d2b79f5
        var t = (a xor (a ushr 15)) * (a or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        val random = (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
        v += drift + (random * 2 - 1) * step
        if (v > hi) v = 2 * hi - v
        if (v < lo) v = 2 * lo - v
        value
    }
}

val Ranges = listOf(
    LineRange("day", "Day", "Last 24 hours", listOf("00:00", "06:00", "12:00", "18:00", "Now"), walk(31, 90, 70.0, 0.05, 6.5, 45.0, 95.0), walk(32, 90, 28.0, 0.0, 5.0, 10.0, 45.0)),
    LineRange("week", "Week", "Last 14 days", listOf("Mar 16", "Mar 19", "Mar 22", "Mar 25", "Mar 29"), walk(11, 90, 60.0, 0.3, 4.0, 45.0, 95.0), walk(12, 90, 40.0, -0.28, 3.0, 10.0, 45.0)),
    LineRange("year", "Year", "1999 – 2024", listOf("1999", "2005", "2011", "2017", "2024"), walk(21, 60, 52.0, 0.62, 2.2, 45.0, 95.0), walk(22, 60, 42.0, -0.3, 2.0, 10.0, 45.0)),
)

fun pointAt(values: List<Double>, fraction: Double, width: Float, height: Float): Offset {
    val n = values.size; val f = fraction.coerceIn(0.0, 1.0) * (n - 1); val i = min(n - 2, floor(f).toInt())
    val v = values[i] + (values[i + 1] - values[i]) * (f - i)
    return Offset((f / (n - 1) * width).toFloat(), (height - v / 100 * height).toFloat())
}

fun linePart(values: List<Double>, from: Double, to: Double, width: Float, height: Float): Path {
    val path = Path()
    if (to - from <= 0.0005) return path
    val start = pointAt(values, from, width, height); path.moveTo(start.x, start.y)
    val n = values.size; val first = floor(from * (n - 1)).toInt() + 1; val last = ceil(to * (n - 1)).toInt() - 1
    for (i in first..last) path.lineTo(i.toFloat() / (n - 1) * width, (height - values[i] / 100 * height).toFloat())
    val end = pointAt(values, to, width, height); path.lineTo(end.x, end.y)
    return path
}
