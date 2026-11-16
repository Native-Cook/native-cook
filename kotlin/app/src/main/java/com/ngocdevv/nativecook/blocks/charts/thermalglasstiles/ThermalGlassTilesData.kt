package com.ngocdevv.nativecook.blocks.charts.thermalglasstiles

import android.graphics.Path
import android.graphics.PathMeasure
import androidx.core.graphics.PathParser
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class ThermalGlassTilesSample(
    var distance: Double = 0.0, var elapsed: Double = 0.0, var bpm: Double = 118.0, var pace: Double = 376.0,
    var contact: Double = 270.0, var oscillation: Double = 10.0,
)

internal class ThermalGlassTilesRoutePoint(val x: Double, val y: Double)

/** The run's data (`tgt-data.ts`): an original plausible 29.6 km dataset of 296 intervals, the route and their lookups. */
internal object ThermalGlassTilesData {
    val RUN: List<ThermalGlassTilesSample> = List(297) { i ->
        val p = i / 296.0
        val hill = exp(-((p - 0.62) / 0.12).pow(2))
        ThermalGlassTilesSample(
            distance = p * 29.6,
            elapsed = p * 10061,
            bpm = 118 + 23 * p + 40 * hill,
            pace = if (i == 296) 376.0 else 376 - 96 * exp(-((p - 0.69) / 0.17).pow(2)),
            contact = 270 + 24 * exp(-((p - 0.39) / 0.11).pow(2)) - 19 * hill,
            oscillation = 10 + 1.7 * exp(-((p - 0.76) / 0.09).pow(2)) - 0.5 * hill,
        )
    }

    /** A different route, redrawn in the source's proportions. */
    const val ROUTE =
        "M 214 245 C 204 240 211 259 195 261 C 182 258 181 249 169 253 C 155 257 158 278 149 292 C 143 300 130 305 132 317 C 135 332 144 339 155 331 C 168 325 178 332 194 327 L 203 323 L 215 328 L 229 335 L 265 263 L 233 245 C 229 255 223 252 214 245 Z"

    fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0) = min(hi, max(lo, v))
    fun mix(a: Double, b: Double, v: Double) = a + (b - a) * v

    fun sampleAt(data: List<ThermalGlassTilesSample>, p: Double, out: ThermalGlassTilesSample) {
        val at = clamp(p) * (data.size - 1)
        val i = floor(at).toInt()
        val f = at - i
        val a = data[i]
        val b = data[min(data.size - 1, i + 1)]
        out.distance = mix(a.distance, b.distance, f)
        out.elapsed = mix(a.elapsed, b.elapsed, f)
        out.bpm = mix(a.bpm, b.bpm, f)
        out.pace = mix(a.pace, b.pace, f)
        out.contact = mix(a.contact, b.contact, f)
        out.oscillation = mix(a.oscillation, b.oscillation, f)
    }

    fun effortAt(s: ThermalGlassTilesSample) = clamp((s.bpm - 118) / 58)

    fun timeText(seconds: Double): String {
        val sec = max(0, seconds.roundToInt())
        return "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
    }

    /** The route resampled to equal-arc points (256). */
    fun resampleRoute(route: String, samples: Int): Pair<Array<ThermalGlassTilesRoutePoint>, Path> {
        val path = PathParser.createPathFromPathData(route)
        val measure = PathMeasure(path, true)
        val len = measure.length
        val pos = FloatArray(2)
        val pts = Array(samples) { i ->
            measure.getPosTan(len * i / (samples - 1), pos, null)
            ThermalGlassTilesRoutePoint(pos[0].toDouble(), pos[1].toDouble())
        }
        return pts to path
    }

    fun nearestRoute(points: Array<ThermalGlassTilesRoutePoint>, x: Double, y: Double, p: Double, all: Boolean = false): Pair<Double, Double> {
        var best = p
        var d = 1e10
        val lo = if (all) 0 else max(0, floor((p - 0.12) * (points.size - 1)).toInt())
        val hi = if (all) points.size - 1 else min(points.size - 1, Math.ceil((p + 0.12) * (points.size - 1)).toInt())
        for (i in lo..hi) {
            val pt = points[i]
            val dd = (pt.x - x) * (pt.x - x) + (pt.y - y) * (pt.y - y)
            if (dd < d) {
                d = dd
                best = i.toDouble() / (points.size - 1)
            }
        }
        return best to sqrt(d)
    }

    fun routeLength(points: Array<ThermalGlassTilesRoutePoint>): Double {
        var length = 0.0
        for (i in 1 until points.size) length += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        return max(1.0, length)
    }
}

