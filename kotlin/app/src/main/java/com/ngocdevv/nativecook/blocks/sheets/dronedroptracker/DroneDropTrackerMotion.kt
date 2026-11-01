package com.ngocdevv.nativecook.blocks.sheets.dronedroptracker

import kotlin.math.hypot
import kotlin.math.min

/** The deterministic route and timeline shared by the Kotlin painter and touch driver. */
internal object DroneDropTrackerMotion {
    const val LOOP = 9.0

    data class Point(val x: Double, val y: Double)

    data class RoutePoint(
        val x: Double,
        val y: Double,
        val dx: Double,
        val dy: Double,
    )

    data class Script(
        val f: Double,
        val drop: Double,
        val carried: Double,
        val back: Double,
        val sheet: Double,
    )

    data class Frame(
        val f: Double,
        val drop: Double,
        val carried: Double,
        val back: Double,
        val sheet: Double,
        val t: Double,
        val live: Boolean,
    )

    val route = listOf(
        Point(0.16, 0.20),
        Point(0.16, 0.42),
        Point(0.50, 0.42),
        Point(0.50, 0.66),
        Point(0.82, 0.66),
        Point(0.82, 0.80),
    )

    fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)

    fun smoother(value: Double): Double {
        val x = clamp01(value)
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0)
    }

    fun lerp(a: Double, b: Double, amount: Double): Double = a + (b - a) * amount

    /** Point along the route at fraction [fraction], retaining the current leg's heading. */
    fun routeAt(fraction: Double): RoutePoint {
        val lengths = DoubleArray(route.lastIndex)
        var total = 0.0
        for (index in 0 until route.lastIndex) {
            val a = route[index]
            val b = route[index + 1]
            val length = hypot(b.x - a.x, b.y - a.y)
            lengths[index] = length
            total += length
        }
        var wanted = clamp01(fraction) * total
        for (index in lengths.indices) {
            val segment = lengths[index]
            if (wanted <= segment || index == lengths.lastIndex) {
                val amount = if (segment > 0.0) min(1.0, wanted / segment) else 0.0
                val a = route[index]
                val b = route[index + 1]
                return RoutePoint(
                    x = lerp(a.x, b.x, amount),
                    y = lerp(a.y, b.y, amount),
                    dx = b.x - a.x,
                    dy = b.y - a.y,
                )
            }
            wanted -= segment
        }
        return RoutePoint(route.first().x, route.first().y, 0.0, 0.0)
    }

    /** Route, lowering, carried state, return flight, and sheet openness at the RN timeline time. */
    fun scriptAt(time: Double): Script {
        val t = ((time % LOOP) + LOOP) % LOOP
        var f = 0.0
        var drop = 0.0
        var carried = 1.0
        var back = 0.0
        if (t < 5.4) {
            f = smoother(t / 5.4)
        } else if (t < 6.6) {
            f = 1.0
            drop = smoother((t - 5.4) / 1.1)
            if (t > 6.5) carried = 0.0
        } else if (t < 8.6) {
            f = 1.0
            drop = 1.0
            carried = 0.0
            back = smoother((t - 6.6) / 2.0)
        } else {
            f = 0.0
            drop = 0.0
            carried = clamp01((t - 8.6) / 0.4)
            back = 1.0
        }
        val sheet = when {
            t < 2.4 -> 0.0
            t < 3.1 -> smoother((t - 2.4) / 0.7)
            t < 7.8 -> 1.0
            else -> 1.0 - smoother((t - 7.8) / 0.7)
        }
        return Script(f, drop, carried, back, sheet)
    }
}
