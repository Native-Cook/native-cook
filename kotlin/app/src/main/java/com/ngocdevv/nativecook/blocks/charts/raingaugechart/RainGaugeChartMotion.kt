package com.ngocdevv.nativecook.blocks.charts.raingaugechart

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Rain Gauge Chart — data and the autoPlay script, as in rainGaugeMotion.ts. Every value is a
// closed-form function of the loop time t (seconds); the drops are pure functions of t and a
// per-drop hash, falling a whole number of times per loop, so the frame at LOOP equals t = 0.

internal const val LOOP = 9f
internal val RainDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
internal val RainFullDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
/** This week's rainfall per day (mm); a tube holds CAPACITY mm. */
internal val RainMm = floatArrayOf(3.2f, 0.8f, 6.4f, 11.6f, 7.9f, 2.1f, 6.6f)
internal val RainNotes = listOf(
    "Drizzle through the morning",
    "A passing shower at 09:40",
    "Steady rain after lunch",
    "Heaviest 15:00 – 17:00",
    "Showers on and off",
    "Light rain at dusk",
    "Overnight rain, easing at 06:00",
)
internal const val CAPACITY = 15f
internal const val TUBES = 7
/** Thursday, the tube the loop lifts. */
internal const val LOOP_LIFT = 3

private const val DRAIN_AT = 1.4f
private const val FILL_AT = 2.5f
private const val FILL_STEP = 0.45f
private const val FILL_FOR = 0.9f
private const val LIFT_AT = 6.0f
private const val LOWER_AT = 7.0f

internal const val DROPS = 170

internal fun rainClamp01(x: Float) = min(max(x, 0f), 1f)

private fun seg(t: Float, a: Float, b: Float) = rainClamp01((t - a) / (b - a))

private fun easeInOut(x: Float): Float {
    val t = rainClamp01(x)
    return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
}

private fun easeOut(x: Float): Float {
    val t = rainClamp01(x)
    return 1f - (1f - t).pow(3)
}

/** The sin hash of the React Native version, in double precision so the drops land the same. */
internal fun rainHash(n: Double): Float {
    val x = sin(n * 12.9898 + 78.233) * 43758.5453
    return (x - floor(x)).toFloat()
}

/** The pose the scene reads. Filled in place every frame. */
internal class RainPose {
    /** Water in each tube (mm). */
    val levels = FloatArray(TUBES)
    /** Surface tilt of each tube's water (dp at the walls, signed). */
    val slosh = FloatArray(TUBES)
    /** The tube lifted out of the rack and how far (0..1). */
    var liftIdx = LOOP_LIFT
    var lift = 0f
    /** Where the rain falls (continuous tube index) and how hard (0..1). */
    var cursor = 0f
    var rain = 0f
    /** The time the drops are drawn at (s). */
    var rainT = 0f
}

internal fun rainLoopPose(t: Float, out: RainPose): RainPose {
    val cursor = min(TUBES - 1f, max(0f, (t - FILL_AT - 0.2f) / FILL_STEP))
    val rain = seg(t, 2.25f, 2.7f) * (1f - seg(t, 5.7f, 6.2f))
    val lift = easeOut(seg(t, LIFT_AT, LIFT_AT + 0.45f)) * (1f - easeInOut(seg(t, LOWER_AT, LOWER_AT + 0.4f)))
    for (i in 0 until TUBES) {
        var level = RainMm[i]
        if (t >= DRAIN_AT && t < FILL_AT) {
            level = RainMm[i] * (1f - easeInOut(seg(t, DRAIN_AT + i * 0.04f, DRAIN_AT + 0.8f + i * 0.04f)))
        } else if (t >= FILL_AT) {
            level = RainMm[i] * easeOut(seg(t, FILL_AT + i * FILL_STEP, FILL_AT + i * FILL_STEP + FILL_FOR))
        }
        // The water rocks while the rain hits it (11 rocks a loop) and when Thursday is lifted.
        val near = exp(-((i - cursor) / 0.9f).pow(2)) * rain
        var tilt = 1.6f * near * sin(2f * PI.toFloat() * 11f * t / LOOP + i * 1.3f)
        if (i == LOOP_LIFT && t > LIFT_AT) {
            val e = t - LIFT_AT
            tilt += 3.2f * exp(-e * 2.2f) * sin(e * 2f * PI.toFloat() * 1.7f) * (1f - seg(t, 8.0f, 8.6f))
        }
        out.levels[i] = level
        out.slosh[i] = tilt
    }
    out.liftIdx = LOOP_LIFT
    out.lift = lift
    out.cursor = cursor
    out.rain = rain
    out.rainT = t
    return out
}

internal fun rainTotalOf(levels: FloatArray): Float {
    var v = 0f
    for (i in 0 until TUBES) v += levels[i]
    return v
}

/** A finger's slosh: a kick that rocks and dies away (seconds since the kick). */
internal fun rainKick(e: Float, size: Float): Float {
    if (e < 0f || e > 4f) return 0f
    return size * exp(-e * 2.4f) * sin(e * 2f * PI.toFloat() * 1.7f)
}

internal const val LIFT_PT = 40f
internal const val LIFT_SCALE = 0.1f

/** Hero geometry in dp. */
internal class RainGeo(val ox: Float, val oy: Float, val w: Float, val h: Float) {
    private val x0 = ox + 52f
    private val x1 = ox + w - 22f
    val pitch = (x1 - x0) / TUBES
    /** Tube centres, half width, top (y0) and bottom (y1). */
    val cx = FloatArray(TUBES) { x0 + pitch * (it + 0.5f) }
    val hw = min(15f, pitch * 0.3f)
    val y1 = oy + h - 50f
    private val tubeH = max(150f, min(300f, h - 200f))
    val y0 = y1 - tubeH
    /** The rack: top board's top edge, the boards' left and right ends, the ground. */
    val board = y0 + tubeH * 0.22f
    val left = x0 - 16f
    val right = x1 + 10f
    val ground = y1 + 12f

    /** The water's top (y, before any lift) for a level in mm. */
    fun levelY(mm: Float) = y1 - 4f - mm / CAPACITY * (y1 - y0 - 26f)
}
