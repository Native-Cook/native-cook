package com.ngocdevv.nativecook.blocks.pickers.deskfanspeed

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin

/**
 * fanScene.ts and fanMotion.ts: the scene's numbers and the film as pure functions of t. The blades' speed is a sum of
 * smooth ramps and their angle its exact integral, scaled so the loop holds a whole number of turns: t = LOOP gives the
 * same frame as t = 0.
 *
 *  0.00 – 0.80  rest: Off, blades still, head at its rest angle
 *  0.80         level 1: spins up over 0.7 s
 *  2.60         level 2
 *  4.30         level 3: the peak, the camera has moved in a little
 *  6.60         back to level 1
 *  7.70         Off: the blades coast down for 1.7 s (to 9.4)
 *  1.10 – 9.20  the head oscillates, two sweeps, under an envelope that eases in and out
 *  9.40 – 10.0  rest; last frame = first frame
 */
internal object FanMotion {
    const val LOOP = 10.0

    // ------------------------------------------------------------ scene (glTF space: y up, metres, fan faces +z)

    /** The head turns about y through this point; the blades about z through BLADES_PIVOT, in the head's frame. */
    val HEAD_PIVOT = floatArrayOf(0f, 0.285f, 0f)
    val BLADES_PIVOT = floatArrayOf(0f, 0f, 0.056f)

    /** The four piano keys, at rest (Off, 1, 2, 3); a pressed key sinks KEY_TRAVEL. */
    val KEY_POSITIONS = arrayOf(
        floatArrayOf(-0.05175f, 0.052f, 0.082f),
        floatArrayOf(-0.01725f, 0.052f, 0.082f),
        floatArrayOf(0.01725f, 0.052f, 0.082f),
        floatArrayOf(0.05175f, 0.052f, 0.082f),
    )
    const val KEY_TRAVEL = 0.0055f

    /** The blur disc's colour (linear), its alpha set per frame. */
    val BLUR_RGB = floatArrayOf(0.69f, 0.46f, 0.15f)

    private val TARGET = doubleArrayOf(0.0, 0.215, 0.0)
    private const val DIST = 1.35
    private const val AZ_REST = -16.0
    private const val AZ_PEAK = -10.0
    private const val EL_REST = 9.0
    private const val EL_PEAK = 8.0
    private const val ZOOM_PEAK = 0.05
    private const val FRAME_W = 0.4
    private const val FRAME_H = 0.5
    const val CAMERA_NEAR = 0.04
    const val CAMERA_FAR = 30.0

    /** Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen. */
    const val IBL_INTENSITY = 30_000f
    val KEY_DIRECTION = floatArrayOf(-0.4f, -0.8f, -0.45f)
    const val KEY_LUX = 70_000f
    const val KEY_KELVIN = 5400f
    val FILL_DIRECTION = floatArrayOf(0.7f, -0.3f, -0.6f)
    const val FILL_LUX = 14_000f
    const val FILL_KELVIN = 7000f
    const val EXPOSURE = 0f

    /** Palette (sRGB). */
    const val BACKDROP = 0xE4D7BA
    const val PANEL = 0xFCFAF4
    const val CARD = 0xFFFFFF
    const val INK = 0x1F2A26
    const val MUTE = 0x6F7670
    const val GREEN = 0x2F6657

    /** Copy: an invented smart-home app. */
    const val APP = "Breeze"
    const val ROOM = "Bedroom fan"
    val LEVELS = listOf("Off", "1", "2", "3")

    /** What each level reads as on the panel: motor rpm and watts. */
    private val RPM = doubleArrayOf(0.0, 620.0, 1040.0, 1480.0)
    private val WATTS = doubleArrayOf(0.0, 14.0, 26.0, 41.0)

    // ------------------------------------------------------------ the film's numbers

    /** Whole turns of the blades in one loop. */
    private const val TURNS = 20.0

    /** Blade speed for each level (turns a second, before the small scale that makes TURNS exact). */
    val SPEEDS = doubleArrayOf(0.0, 1.5, 2.9, 4.4)

    /** The film's level changes: time, new level, ramp length (s). */
    private val RAMP_AT = doubleArrayOf(0.8, 2.6, 4.3, 6.6, 7.7)
    private val RAMP_LEVEL = intArrayOf(1, 2, 3, 1, 0)
    private val RAMP_LEN = doubleArrayOf(0.7, 0.7, 0.7, 0.9, 1.7)

    /** The head: rest angle and the film's sweep (deg), its sweeps per loop and envelope. */
    const val YAW_REST = -10.0
    private const val OSC_DEG = 24.0
    private const val OSC_CYCLES = 2.0
    private const val OSC_IN0 = 1.1
    private const val OSC_IN1 = 2.4
    private const val OSC_OUT0 = 7.4
    private const val OSC_OUT1 = 9.2

    /** A finger's aim: degrees for a drag across the screen, and the most it turns (rubber-banded past it). */
    const val AIM_SPAN = 110.0
    const val AIM_MAX = 42.0

    /** A finger's speed changes: time constants (s) of the motor spinning up and coasting down. */
    const val SPIN_UP_TAU = 0.42
    const val SPIN_DOWN_TAU = 0.95

    /** Airflow dashes: the pattern's length (pt) and how many patterns pass per blade turn (a half, so TURNS stays whole). */
    const val FLOW_PERIOD = 72f
    const val FLOW_PER_TURN = 0.5

    /** A pressed key takes this long to go down (s, around its moment). */
    private const val KEY_LEAD = 0.05
    private const val KEY_FALL = 0.09

    // ------------------------------------------------------------ helpers

    fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    private fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))
    private fun lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k
    private fun smooth(x: Double): Double = x * x * (3 - 2 * x)

    /** The integral of smooth(x) from 0 to x. */
    private fun smoothIntegral(x: Double): Double = x * x * x - x * x * x * x / 2

    /** The turns of the film's speed profile from 0 to t, before scaling. */
    private fun rawTurns(t: Double): Double {
        var total = 0.0
        var prev = 0.0
        for (i in RAMP_AT.indices) {
            val at = RAMP_AT[i]
            val to = SPEEDS[RAMP_LEVEL[i]]
            val len = RAMP_LEN[i]
            val dv = to - prev
            prev = to
            if (t <= at) continue
            total += if (t >= at + len) dv * (t - at - len / 2) else dv * len * smoothIntegral((t - at) / len)
        }
        return total
    }

    private val TURN_SCALE = TURNS / rawTurns(LOOP)

    // ------------------------------------------------------------ the film

    /** The film's blade angle, in turns (0 .. TURNS). */
    fun filmTurns(t: Double): Double = rawTurns(t) * TURN_SCALE

    /** The film's blade speed (turns a second). */
    fun filmSpeed(t: Double): Double {
        var v = 0.0
        var prev = 0.0
        for (i in RAMP_AT.indices) {
            val to = SPEEDS[RAMP_LEVEL[i]]
            v += (to - prev) * smooth(seg(t, RAMP_AT[i], RAMP_AT[i] + RAMP_LEN[i]))
            prev = to
        }
        return v * TURN_SCALE
    }

    /** The level the film has selected. */
    fun filmLevel(t: Double): Int {
        var level = 0
        for (i in RAMP_AT.indices) if (t >= RAMP_AT[i]) level = RAMP_LEVEL[i]
        return level
    }

    /** The film's head angle (deg). */
    fun filmYaw(t: Double): Double {
        val env = smooth(seg(t, OSC_IN0, OSC_IN1)) * (1 - smooth(seg(t, OSC_OUT0, OSC_OUT1)))
        return YAW_REST + OSC_DEG * env * sin(2 * PI * OSC_CYCLES * t / LOOP)
    }

    /** How far key i is down in the film, 0..1. */
    fun filmKey(t: Double, i: Int): Double {
        var v = if (i == 0) 1.0 else 0.0
        var prev = 0
        for (k in RAMP_AT.indices) {
            val e = smooth(seg(t, RAMP_AT[k] - KEY_LEAD, RAMP_AT[k] + KEY_FALL))
            if (RAMP_LEVEL[k] == i) v += e
            if (prev == i) v -= e
            prev = RAMP_LEVEL[k]
        }
        return clamp01(v)
    }

    /** The film's finger on the segmented control: out = [opacity, pressed, level it is over]. */
    fun tapGhost(t: Double, out: DoubleArray) {
        var best = 0
        var dist = 1e9
        for (k in RAMP_AT.indices) {
            val d = abs(t - RAMP_AT[k])
            if (d < dist) {
                dist = d
                best = k
            }
        }
        val at = RAMP_AT[best]
        out[0] = seg(t, at - 0.3, at - 0.08) * (1 - seg(t, at + 0.1, at + 0.3))
        out[1] = seg(t, at - 0.08, at) * (1 - seg(t, at + 0.04, at + 0.16))
        out[2] = RAMP_LEVEL[best].toDouble()
    }

    // ------------------------------------------------------------ what the speed reads as

    /** How much the blades are a blur at this speed, 0..1. */
    fun blurAt(speed: Double): Double = smooth(seg(speed, 0.9, 3.6)) * 0.85

    /** A reading between the levels' values, from the blade speed. */
    private fun readAt(speed: Double, table: DoubleArray): Double {
        for (i in 0 until SPEEDS.size - 1) {
            if (speed <= SPEEDS[i + 1]) return lerp(table[i], table[i + 1], seg(speed, SPEEDS[i], SPEEDS[i + 1]))
        }
        return table[table.size - 1]
    }

    /** JavaScript's Math.round: halves go up. */
    private fun roundJs(x: Double): Int = floor(x + 0.5).toInt()

    fun rpmAt(speed: Double): Int = roundJs(readAt(speed, RPM) / 10) * 10
    fun wattsAt(speed: Double): Int = roundJs(readAt(speed, WATTS))

    // ------------------------------------------------------------ camera

    /** tan(half the vertical field of view) that keeps FRAME_W x FRAME_H around the target in a stage of this aspect. */
    fun tanHalfFovY(aspect: Double): Double = max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST

    /** The camera for a blade speed (it leans in as the fan works): out = [eye xyz, target xyz]. */
    fun cameraAt(speed: Double, out: DoubleArray) {
        val k = smooth(clamp01(speed / SPEEDS[3]))
        val az = lerp(AZ_REST, AZ_PEAK, k) * PI / 180
        val el = lerp(EL_REST, EL_PEAK, k) * PI / 180
        val d = DIST * (1 - ZOOM_PEAK * k)
        out[0] = TARGET[0] + d * cos(el) * sin(az)
        out[1] = TARGET[1] + d * sin(el)
        out[2] = TARGET[2] + d * cos(el) * cos(az)
        out[3] = TARGET[0]
        out[4] = TARGET[1]
        out[5] = TARGET[2]
    }

    /** A finger's aim from its drag (dp), rubber-banded past AIM_MAX either side of the centre. */
    fun aimFromDrag(from: Double, dx: Double, width: Double): Double {
        val a = from + dx / width * AIM_SPAN
        val m = abs(a)
        if (m <= AIM_MAX) return a
        val over = m - AIM_MAX
        return sign(a) * (AIM_MAX + over * 12 / (over + 12))
    }
}
