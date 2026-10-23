// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/** Measured in the supplied 60fps clip; drawing fractions use the 274px track.
 * This is an explanatory replay, not the latency of a live control. */
const val referenceDuration = 10050.0

object G {
    const val width = 1000.0
    const val height = 284.0

    object Track { const val x = 80.0; const val y = 94.0; const val width = 523.0; const val height = 104.0 }
    object Dial { const val x = 849.0; const val y = 146.0; const val radius = 100.0 }
    object Brightness { const val x = 676.0; const val y = 88.0; const val radius = 47.0 }
    object Volume { const val x = 680.0; const val y = 198.0; const val radius = 47.0 }

    // Percentage readout box inside the track (right-aligned glyph run).
    object Readout { const val x = 464.0; const val width = 110.0; const val y = 121.0; const val line = 38.0; const val size = 30.0 }
}

typealias Point = Pair<Double, Double>

fun sample(points: List<Point>, time: Double): Double {
    if (time <= points[0].first) return points[0].second
    for (i in 1 until points.size) {
        if (time <= points[i].first) {
            val (a, x) = points[i - 1]
            val (b, y) = points[i]
            return x + ((y - x) * (time - a)) / (b - a)
        }
    }
    return points.last().second
}

private fun pts(vararg v: Double): List<Point> = v.toList().chunked(2) { it[0] to it[1] }

private val levelWidths = pts(
    0.0, 0.0, 0.14, 61.0 / 274, 0.52, 163.0 / 274, 0.53, 166.0 / 274, 0.72, 212.0 / 274, 1.0, 1.0,
)

fun fillForLevel(level: Double) = sample(levelWidths, max(0.0, min(1.0, level)))

private val positionLevels = levelWidths.map { (level, position) -> position to level }

fun levelForFill(position: Double) = sample(positionLevels, max(0.0, min(1.0, position)))

private val fillPixels = pts(
    0.0, 26.0, 200.0, 52.0, 300.0, 96.0, 500.0, 137.0, 750.0, 156.0, 1000.0, 163.0, 1983.0, 163.0,
    2083.0, 142.0, 2200.0, 82.0, 2350.0, 48.0, 2450.0, 40.0, 2550.0, 43.0, 2650.0, 63.0, 2800.0, 101.0,
    3000.0, 126.0, 3250.0, 144.0, 3500.0, 153.0, 4000.0, 161.0, 4383.0, 166.0, 4667.0, 191.0,
    5000.0, 206.0, 5500.0, 211.0, 6000.0, 212.0, 6583.0, 211.0, 6683.0, 204.0, 6833.0, 175.0,
    7000.0, 120.0, 7250.0, 88.0, 7500.0, 72.0, 8000.0, 62.0, 8500.0, 61.0, 10050.0, 61.0,
)
private val lightAlpha = pts(
    0.0, 0.0, 200.0, 0.35, 600.0, 0.75, 1100.0, 1.0, 2200.0, 1.0, 2450.0, 0.55, 2750.0, 1.0,
    9100.0, 1.0, 9400.0, 0.5, 9700.0, 0.0, 10050.0, 0.0,
)
private val readoutAlpha = pts(
    0.0, 0.0, 270.0, 0.0, 630.0, 1.0, 2000.0, 1.0, 2400.0, 0.0, 2750.0, 0.0, 3150.0, 1.0, 4400.0, 1.0,
    4640.0, 0.0, 4950.0, 1.0, 6600.0, 1.0, 7000.0, 0.0, 7300.0, 1.0, 9100.0, 1.0, 9700.0, 0.0,
)

// Label luminance sampled from the same source ROI (97th percentile), relative
// to its settled foreground/background. The label fade leads the fill collapse.
private val brightnessLabelAlpha = pts(
    0.0, 0.0, 100.0, 0.13, 200.0, 0.34, 300.0, 0.56, 500.0, 0.99, 1900.0, 0.97, 2000.0, 0.72,
    2050.0, 0.57, 2100.0, 0.43, 2150.0, 0.29, 2200.0, 0.12, 2250.0, 0.02, 2300.0, 0.0,
)
private val volumeLabelAlpha = pts(
    2483.0, 0.0, 2500.0, 0.04, 2550.0, 0.12, 2600.0, 0.24, 2700.0, 0.46, 2800.0, 0.7, 3000.0, 1.0,
)
private val reducedMix = pts(2000.0, 0.0, 2200.0, 1.0)
private val liveMix = pts(2433.0, 0.0, 2583.0, 1.0)
private val blurPoints = pts(0.0, 0.0, 2150.0, 0.0, 2400.0, 14.0, 2500.0, 10.0, 2700.0, 0.0)

data class Pose(
    val mode: Int,
    val level: Double,
    val display: Int,
    val fill: Double,
    val light: Double,
    val number: Double,
    val brightnessLabel: Double,
    val volumeLabel: Double,
    val mix: Double,
    val blur: Double,
    val phase: Int,
)

/** JS Math.round: halves round toward +∞. */
fun jsRound(v: Double): Double = kotlin.math.floor(v + 0.5)

fun referencePose(timeMs: Double, reduced: Boolean = false): Pose {
    val time = max(0.0, min(referenceDuration, timeMs))
    val mode = if (time < 2000) 0 else 1
    val level = if (time < 2000) 0.52 else if (time < 4400) 0.53 else if (time < 6600) 0.72 else 0.14
    val display = if (time < 2500) 52 else if (time < 4640) 53 else if (time < 7000) 72 else 14
    val mix = sample(if (reduced) reducedMix else liveMix, time)
    return Pose(
        mode = mode,
        level = level,
        display = if (reduced) jsRound(level * 100).toInt() else display,
        fill = if (reduced) fillForLevel(level) else sample(fillPixels, time) / 274,
        light = if (reduced) (if (time < 9700) 1.0 else 0.0) else sample(lightAlpha, time),
        number = if (reduced) 1.0 else sample(readoutAlpha, time),
        brightnessLabel = if (reduced) (1 - mode).toDouble() else sample(brightnessLabelAlpha, time),
        volumeLabel = if (reduced) mode.toDouble() else sample(volumeLabelAlpha, time),
        mix = mix,
        blur = if (reduced) 0.0 else sample(blurPoints, time),
        phase = if (time < 2000) 0 else if (time < 4400) 1 else if (time < 6600) 2 else if (time < 9100) 3 else 4,
    )
}

/** iOS-style resistance past a hard limit: follows the finger at first, then
 * approaches `limit` asymptotically. Sign is preserved; 0 stays 0. */
fun rubberBand(overflow: Double, limit: Double): Double {
    val distance = abs(overflow)
    if (distance == 0.0 || limit <= 0) return 0.0
    val resisted = (1 - 1 / ((distance * 0.55) / limit + 1)) * limit
    return sign(overflow) * resisted
}

