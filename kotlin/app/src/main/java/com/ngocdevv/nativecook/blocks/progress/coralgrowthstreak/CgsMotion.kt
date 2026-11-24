// Coral Growth Streak — the loop, the simulation's constants and the scripted week, in closed form
// (cgs-motion.ts). The coral itself is the one thing that is not a function of time: it is a
// Gray-Scott field stepped at SIM_STEP_HZ from the seeds below, seeded on exact step numbers, so every
// loop grows the same reef; it is dissolved in the view and cleared at t = 0, so frame 10 s = frame 0.
package com.ngocdevv.nativecook.blocks.progress.coralgrowthstreak

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal const val LOOP = 10.0

/** grid cells per side; the reef window shows the inscribed circle */
internal const val SIM_GRID = 256
/** fixed steps a second, however fast the display refreshes (20 a frame at 60 Hz) */
internal const val SIM_STEP_HZ = 1200
internal const val SIM_FEED = 0.0545f
internal const val SIM_KILL = 0.062f
/** outside the open radius the kill rate is raised by this much, which nothing survives */
internal const val SIM_WALL_KILL = 0.012f
/** half width of that wall's edge, cells */
internal const val SIM_WALL_WIDTH = 5f
/** a seed: v += exp(-r² / radius²), u -= half of it */
internal const val SIM_SEED_RADIUS = 3f
/** most steps one frame may run (a stall is caught up over the next frames, not in one spike) */
internal const val SIM_MAX_STEPS = 40
/** seeds one frame can carry */
internal const val SIM_SEED_SLOTS = 8

internal const val DAYS = 7

/** day i is completed at T_FIRST + i * T_EVERY */
private const val T_FIRST = 0.6
private const val T_EVERY = 0.9
/** a day's dot fills over this long (ease-out-back), the count rolls over the same time */
internal const val T_DOT = 0.42
/** the reef's open radius eases to the day's ring over this long in the fallback */
private const val T_RING = 0.7
private const val T_BLOOM_START = 7.0
private const val T_BLOOM_END = 8.2
private const val T_FADE_START = 8.5
private const val T_FADE_END = 9.35

/** a touch hands the reef back this long after it ends */
internal const val IDLE = 2.0
/** then the coral dissolves over the ease and the loop restarts at the total */
internal const val RETURN_EASE = 0.85
internal const val RETURN_TOTAL = 1.0

/** the frame handed to the render thread */
internal object Frame {
    const val EPOCH = 0
    const val STEPS = 1
    const val RADIUS = 2
    const val LEVEL = 3
    const val BLOOM = 4
    const val PHASE = 5
    const val SEEDS = 6
    const val LENGTH = SEEDS + SIM_SEED_SLOTS * 4
}

internal fun clamp01(v: Double) = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v

/** 0 before a, 1 after b */
internal fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))

internal fun smooth(v: Double) = v * v * (3.0 - 2.0 * v)

/** overshoots to 1.1 at 0.7, settles on 1 */
internal fun easeOutBack(v: Double): Double {
    val u = v - 1.0
    return 1.0 + 2.70158 * u * u * u + 1.70158 * u * u
}

/** when day i (0…6) is completed, seconds */
internal fun dayTime(i: Int) = T_FIRST + i * T_EVERY

/** the streak shown at t, a real number that rolls from one count to the next */
internal fun countAt(t: Double): Double {
    var n = 0.0
    for (i in 0 until DAYS) n += smooth(seg(t, dayTime(i), dayTime(i) + T_DOT))
    return n
}

/** the open radius after d completed days, as a fraction of the reef's radius */
internal fun ringFrac(d: Int) = sqrt(d.toDouble() / DAYS) * 1.06

/** the same radius, eased from ring to ring (what the fallback draws) */
internal fun ringSoftAt(t: Double): Double {
    var r = 0.0
    for (i in 0 until DAYS) r += (ringFrac(i + 1) - ringFrac(i)) * smooth(seg(t, dayTime(i), dayTime(i) + T_RING))
    return r
}

/** the bloom's travel 0…1 at t */
internal fun bloomAt(t: Double) = seg(t, T_BLOOM_START, T_BLOOM_END)

/** how much of the coral is shown at t: 1, then 0 after the fade */
internal fun levelAt(t: Double) = 1.0 - smooth(seg(t, T_FADE_START, T_FADE_END))

private fun hash1(n: Double): Double {
    val x = sin(n * 127.1 + 311.7) * 43758.5453
    return x - floor(x)
}

/** the seeds of day i (0…6) as x, y pairs in 0…1 of the grid: two at the centre, then a ring of 4…8 */
private fun seedsFor(day: Int): FloatArray {
    if (day == 0) return floatArrayOf(0.5f, 0.5f, 0.59f, 0.45f)
    val n = min(SIM_SEED_SLOTS, 3 + day)
    val out = FloatArray(n * 2)
    val mid = ((ringFrac(day) + ringFrac(day + 1)) / 2.0 / 1.06) * 0.5 * 0.97
    for (j in 0 until n) {
        val a = (2.0 * PI * (j + hash1(day * 7.3 + j))) / n + day * 0.9
        val r = mid * (0.94 + 0.1 * hash1(day * 3.1 + j * 5.7))
        out[j * 2] = (0.5 + r * cos(a)).toFloat()
        out[j * 2 + 1] = (0.5 + r * sin(a)).toFloat()
    }
    return out
}

/** SEEDS[day] = x0, y0, x1, y1, … */
internal val SEEDS: List<FloatArray> = List(DAYS) { seedsFor(it) }
/** the step each day's seeds go in on: 720, 1800, … 7200 */
internal val DAY_STEP: IntArray = IntArray(DAYS) { Math.round((T_FIRST + it * T_EVERY) * SIM_STEP_HZ).toInt() }
