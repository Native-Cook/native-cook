package com.ngocdevv.nativecook.blocks.charts.rainwindowforecast

import kotlin.math.*

internal object RainWindowForecastMotion {
    const val LOOP = 10.0
    const val POINTS = 24
    const val REGROW = 5.8
    data class Point(val x: Double = 0.0, val y: Double = 0.0, val age: Double = -1.0)
    data class Frame(val wipe: List<Point>, val finger: Double = 0.0, val fx: Double = 0.0, val fy: Double = 0.0, val press: Double = 0.0)
    fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
    fun ease(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun out(x: Double) = 1 - (1 - x).pow(3)
    fun stroke(s: Double) = Pair(0.18 + 0.64 * s, 0.42 + 0.07 * sin(s * PI * 2) - 0.04 * s)
    private val passage = List(POINTS) { i ->
        val s = i.toDouble() / (POINTS - 1)
        var lo = 0.0
        var hi = 1.0
        repeat(14) {
            val mid = (lo + hi) / 2
            if (ease(mid) < s) lo = mid else hi = mid
        }
        1 + 1.4 * lo
    }
    fun frame(t: Double, w: Double, h: Double): Frame {
        val wipe = List(POINTS) { i ->
            val p = stroke(i.toDouble() / (POINTS - 1))
            Point(p.first * w, p.second * h, if (t >= passage[i]) t - passage[i] else -1.0)
        }
        val p = stroke(ease(seg(t, 1.0, 2.4)))
        val finger = out(seg(t, 0.82, 0.97)) * (1 - out(seg(t, 2.4, 2.58)))
        return Frame(wipe, finger, p.first * w, p.second * h, finger)
    }
}
