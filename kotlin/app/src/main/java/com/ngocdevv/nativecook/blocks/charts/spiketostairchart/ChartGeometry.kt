package com.ngocdevv.nativecook.blocks.charts.spiketostairchart

import kotlin.math.*

internal fun clamp(n: Double, lo: Double = 0.0, hi: Double = 1.0) = n.coerceIn(lo, hi)

internal fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p

internal fun smooth(n: Double): Double {
    val p = clamp(n)
    return p * p * (3 - 2 * p)
}

internal fun filmEase(n: Double): Double {
    val x = clamp(n)
    var lo = 0.0
    var hi = 1.0
    var t = x
    repeat(16) {
        t = (lo + hi) / 2
        val q = 1 - t
        val v = 3 * q * q * t * .77 + 3 * q * t * t * .175 + t * t * t
        if (v < x) lo = t else hi = t
    }
    return 3 * (1 - t) * t * t + t * t * t
}

internal fun pulse(t: Double, up: Double, down: Double) =
    if (t < 0) 0.0 else if (t < up) smooth(t / up) else 1 - smooth((t - up) / down)

internal fun springProgress(t: Double, damping: Double, stiffness: Double): Double {
    if (t <= 0) return 0.0
    val a = damping / 2
    val w = sqrt(stiffness - a * a)
    return 1 - exp(-a * t) * (cos(w * t) + a / w * sin(w * t))
}

internal data class ChartSample(
    val u: Double,
    val v: Double,
    val x: Double,
    val y: Double,
    var a: Double,
    val angle: Double = 0.0,
)

internal class ChartCurve(segments: List<DoubleArray>) {
    val points = mutableListOf(ChartSample(0.0, 0.0, 64.0, 510.0, 0.0))
    val joints = mutableListOf(0.0)

    init {
        var startU = 0.0
        var startV = 0.0
        var length = 0.0
        for (s in segments) {
            for (j in 1..64) {
                val t = j / 64.0
                val q = 1 - t
                val u =
                    q * q * q * startU +
                        3 * q * q * t * s[0] +
                        3 * q * t * t * s[2] +
                        t * t * t * s[4]
                val v =
                    q * q * q * startV +
                        3 * q * q * t * s[1] +
                        3 * q * t * t * s[3] +
                        t * t * t * s[5]
                val x = 64 + u * 274
                val y = 510 - v * 274
                length += hypot(x - points.last().x, y - points.last().y)
                points.add(ChartSample(u, v, x, y, length))
            }
            joints.add(length)
            startU = s[4]
            startV = s[5]
        }
        points.forEach { it.a /= length }
        joints.indices.forEach { joints[it] /= length }
    }
}

internal data class ChartPiece(val points: List<ChartSample>, val start: Double, val end: Double) {
    val cx = (points.first().x + points.last().x) / 2
    val cy = (points.minOf { it.y } + points.maxOf { it.y }) / 2
    val bottom = points.maxOf { it.y }
    val left = points.first().x
    val right = points.last().x
}

internal object ChartGeometry {
    val motivation =
        ChartCurve(
            listOf(
                doubleArrayOf(.06, .45, .19, .925, .28, .925),
                doubleArrayOf(.345, .925, .37, .52, .455, .52),
                doubleArrayOf(.52, .52, .535, .825, .595, .825),
                doubleArrayOf(.63, .825, .65, .718, .682, .718),
                doubleArrayOf(.71, .718, .72, .79, .75, .79),
                doubleArrayOf(.81, .79, .87, .42, .957, 0.0),
            )
        )
    val discipline =
        ChartCurve(
            listOf(
                doubleArrayOf(.03, .045, .06, .085, .1, .085),
                doubleArrayOf(.135, .085, .142, .069, .17, .069),
                doubleArrayOf(.21, .069, .185, .24, .235, .24),
                doubleArrayOf(.305, .24, .319, .221, .375, .221),
                doubleArrayOf(.415, .221, .39, .397, .44, .397),
                doubleArrayOf(.484, .397, .4928, .419, .528, .431),
                doubleArrayOf(.568, .443, .543, .602, .593, .602),
                doubleArrayOf(.6255, .602, .632, .62, .658, .632),
                doubleArrayOf(.698, .644, .673, .835, .723, .835),
                doubleArrayOf(.85, .835, .975, .92, 1.06, 1.14),
                doubleArrayOf(1.105, 1.26, 1.15, 1.45, 1.185, 1.66),
                doubleArrayOf(1.205, 1.78, 1.225, 1.95, 1.24, 2.08),
            )
        )
    val curves = listOf(motivation, discipline)
    val pieces =
        (0..5).map {
            ChartPiece(
                motivation.points.subList(it * 64, it * 64 + 65),
                motivation.joints[it],
                motivation.joints[it + 1],
            )
        }
    val stairEnd = discipline.joints[9]
    val risers = doubleArrayOf(.2, .405, .558, .688, .97)
    val extrema = doubleArrayOf(.28, .455, .595, .682, .75)
    val treads = doubleArrayOf(.13, .3, .482, .624, .8)
    val shards =
        listOf(
            doubleArrayOf(332.0, 196.0, 64.0, 20.0, 1.0),
            doubleArrayOf(352.0, 266.0, 44.0, -35.0, .9),
            doubleArrayOf(300.0, 168.0, 30.0, 70.0, .8),
            doubleArrayOf(372.0, 150.0, 24.0, 10.0, .85),
            doubleArrayOf(318.0, 228.0, 18.0, -60.0, .8),
            doubleArrayOf(120.0, 214.0, 14.0, 30.0, .7),
        )
}

internal fun lookup(d: Int, n: Double, arc: Boolean): ChartSample {
    val points = ChartGeometry.curves[d].points
    fun coordinate(p: ChartSample) = if (arc) p.a else p.u
    var lo = 0
    var hi = points.lastIndex
    val value = clamp(n, coordinate(points[0]), coordinate(points[hi]))
    while (hi - lo > 1) {
        val m = (hi + lo) shr 1
        if (coordinate(points[m]) < value) lo = m else hi = m
    }
    val a = points[lo]
    val b = points[hi]
    val p =
        (value - coordinate(a)) / (coordinate(b) - coordinate(a)).let { if (it == 0.0) 1.0 else it }
    return ChartSample(
        mix(a.u, b.u, p),
        mix(a.v, b.v, p),
        mix(a.x, b.x, p),
        mix(a.y, b.y, p),
        mix(a.a, b.a, p),
        atan2(b.y - a.y, b.x - a.x),
    )
}

internal fun atArc(d: Int, a: Double) = lookup(d, a, true)

internal fun atU(d: Int, u: Double) = lookup(d, u, false)

internal fun nearestTread(u: Double) = ChartGeometry.treads.minBy { abs(it - u) }

// The switch uses the original (0.23, 1, 0.32, 1) press curve.
internal fun pressEase(n: Double): Double {
    val x = clamp(n)
    var lo = 0.0
    var hi = 1.0
    repeat(20) {
        val t = (lo + hi) / 2
        val q = 1 - t
        val value = 3 * q * q * t * .23 + 3 * q * t * t * .32 + t * t * t
        if (value < x) lo = t else hi = t
    }
    return 1 - (1 - (lo + hi) / 2).pow(3)
}
