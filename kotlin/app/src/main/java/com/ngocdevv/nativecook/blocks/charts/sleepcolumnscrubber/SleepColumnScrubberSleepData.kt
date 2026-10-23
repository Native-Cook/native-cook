package com.ngocdevv.nativecook.blocks.charts.sleepcolumnscrubber

import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The sleep stages a night is split into, in the order of their colours. */
enum class SleepStage(val color: Color, val label: String) {
    Awake(Color(0xFFFF8A3D), "Awake"),
    Rem(Color(0xFFB000FF), "REM"),
    Core(Color(0xFF1E96FF), "Core"),
    Deep(Color(0xFF1DE9B6), "Deep"),
}

data class SleepRun(val stage: SleepStage, val minutes: Double)

/**
 * One night: when sleep started and ended, in minutes from midnight (negative before it).
 *
 * @param stages Stage runs from [asleep] on, each with its length in minutes; generated when left out.
 */
data class SleepNight(
    val weekday: String,
    val day: Int,
    val asleep: Double,
    val awake: Double,
    val stages: List<SleepRun>? = null,
)

// Two weeks of nights, the last one tonight's (00:42–08:23).
val SleepNights = listOf(
    SleepNight("Th", 2, 40.0, 413.0),
    SleepNight("Fr", 3, 75.0, 505.0),
    SleepNight("Sa", 4, 38.0, 460.0),
    SleepNight("Su", 5, 110.0, 435.0),
    SleepNight("Mo", 6, 3.0, 502.0),
    SleepNight("Tu", 7, 50.0, 380.0),
    SleepNight("We", 8, 15.0, 470.0),
    SleepNight("Th", 9, 26.0, 520.0),
    SleepNight("Fr", 10, 48.0, 492.0),
    SleepNight("Sa", 11, 62.0, 405.0),
    SleepNight("Su", 12, 34.0, 345.0),
    SleepNight("Mo", 13, -18.0, 455.0),
    SleepNight("Tu", 14, 8.0, 300.0),
    SleepNight("We", 15, 42.0, 503.0),
)

// Stage runs last from RUN_MIN to RUN_MIN + RUN_SPREAD minutes; REM and awake runs are shorter.
private const val RUN_MIN = 4.0
private const val RUN_SPREAD = 26.0
private const val SHORT_RUN = 0.45
// How likely each stage is: awake always this rare, deep fading through the night, REM growing.
private const val AWAKE = 0.03
private const val DEEP_EARLY = 0.5
private const val REM_EARLY = 0.06
private const val REM_LATE = 0.2

/** A fixed value in [0, 1) for a night and a run, so every platform draws the same nights. */
internal fun hash(a: Double, b: Double): Double {
    val x = sin(a * 127.1 + b * 311.7) * 43758.5453
    return x - floor(x)
}

/**
 * The stage runs of a night: deep sleep comes early, REM late, core all night, and a short awake
 * now and then. Each run's length and stage come from `hash(night, run)`.
 */
internal fun stagesOf(night: SleepNight, index: Int): List<SleepRun> {
    night.stages?.let { return it }
    val runs = mutableListOf<SleepRun>()
    val length = night.awake - night.asleep
    var at = 0.0
    var run = 0
    while (at < length) {
        val through = at / length
        val pick = hash(index.toDouble(), (run * 2 + 1).toDouble())
        val deep = DEEP_EARLY * (1 - through)
        val rem = REM_EARLY + (REM_LATE - REM_EARLY) * through
        val stage = when {
            pick < AWAKE -> SleepStage.Awake
            pick < AWAKE + deep -> SleepStage.Deep
            pick < AWAKE + deep + rem -> SleepStage.Rem
            else -> SleepStage.Core
        }
        val span = RUN_MIN + hash(index.toDouble(), (run * 2).toDouble()) * RUN_SPREAD
        val minutes = min(length - at, if (stage == SleepStage.Rem || stage == SleepStage.Awake) span * SHORT_RUN else span)
        runs += SleepRun(stage, minutes)
        at += minutes
        run++
    }
    return runs
}

/** 42 → "00:42", -18 → "23:42". */
internal fun clock(minutes: Double): String {
    val m = ((floor(minutes + 0.5).toInt() % 1440) + 1440) % 1440
    return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
}

internal fun mean(values: List<Double>) = if (values.isEmpty()) 0.0 else values.sum() / values.size

/** The standard deviation, in the values' unit. */
internal fun spread(values: List<Double>): Double {
    val middle = mean(values)
    return sqrt(mean(values.map { (it - middle) * (it - middle) }))
}
