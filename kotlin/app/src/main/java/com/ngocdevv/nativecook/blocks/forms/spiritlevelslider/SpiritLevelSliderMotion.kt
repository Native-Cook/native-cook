package com.ngocdevv.nativecook.blocks.forms.spiritlevelslider

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/*
 Spirit Level Slider — the tilt timeline and the bubble's physics. Pure functions of their inputs.

 autoPlay (LOOP = 8 s), level angle θ (degrees, right end up is positive):
   0.0 – 1.2   rest at 0°.
   1.2 – 3.0   tilt to 28° (ease-in-out, a small dip the other way first: anticipation).
   3.0 – 4.4   hold 28° (6/12, Standard).
   4.4 – 5.4   overshoot up to 40° (ease-in-out).
   5.4 – 6.4   back down to 18° (ease-in-out).
   6.4 – 7.6   return to 0° (ease-in-out).
   7.6 – 8.0   rest.
 The bubble runs to the higher end of the vial on a spring (ω 7 rad/s, ζ 0.42), integrated over
 four loops and the last kept, so the loop is exact.
 */

/** The autoPlay loop, seconds (the manifest's `recording.loop`). */
const val LOOP = 8.0

internal object SpiritMotion {
    /** The tilt range a finger can set (rubber-band beyond). */
    const val MIN_ANGLE = -6.0
    const val MAX_ANGLE = 48.0
    private const val BUBBLE_OMEGA = 7.0
    private const val BUBBLE_ZETA = 0.42
    private const val TABLE_RATE = 120

    val PITCH_WORDS = arrayOf("Flat", "Low slope", "Standard", "Steep")
    private val PITCH_BOUNDS = doubleArrayOf(5.0, 18.0, 37.0)

    fun clamp(v: Double, a: Double, b: Double) = min(max(v, a), b)
    fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a), 0.0, 1.0)
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun smooth(a: Double, b: Double, x: Double): Double {
        val t = clamp((x - a) / (b - a), 0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** The level's angle at loop time t. */
    fun angleAt(t: Double): Double = when {
        t < 1.2 -> 0.0
        t < 3.0 -> {
            val u = seg(t, 1.2, 3.0)
            // A small dip before the lift (anticipation), gone by a quarter of the move.
            val dip = -0.9 * sin(PI * clamp(u / 0.25, 0.0, 1.0)) * (1 - u)
            28 * easeInOut(u) + dip
        }
        t < 4.4 -> 28.0
        t < 5.4 -> mix(28.0, 40.0, easeInOut(seg(t, 4.4, 5.4)))
        t < 6.4 -> mix(40.0, 18.0, easeInOut(seg(t, 5.4, 6.4)))
        t < 7.6 -> mix(18.0, 0.0, easeInOut(seg(t, 6.4, 7.6)))
        else -> 0.0
    }

    /** Where the bubble wants to sit for a tilt, as a share of its travel (−1 … 1, past 1 = pinned). */
    fun bubbleGoal(angle: Double) = clamp(sin(angle * PI / 180) / sin(42 * PI / 180), -1.15, 1.15)

    /** The bubble's travel (share) over one loop: [position, velocity] pairs, periodic. */
    fun buildBubbleTable(): DoubleArray {
        val n = (LOOP * TABLE_RATE).roundToInt()
        val sub = 4
        val dt = 1.0 / (TABLE_RATE * sub)
        var x = 0.0
        var v = 0.0
        val out = DoubleArray(n * 2)
        for (loop in 0 until 4) {
            for (i in 0 until n) {
                for (s in 0 until sub) {
                    val target = bubbleGoal(angleAt((i * sub + s) * dt))
                    val a = -BUBBLE_OMEGA * BUBBLE_OMEGA * (x - target) - 2 * BUBBLE_ZETA * BUBBLE_OMEGA * v
                    v += a * dt
                    x += v * dt
                }
                if (loop == 3) {
                    out[i * 2] = x
                    out[i * 2 + 1] = v
                }
            }
        }
        return out
    }

    /** [position, velocity] of the bubble at loop time t, into `out`. */
    fun sampleBubble(table: DoubleArray, t: Double, out: DoubleArray) {
        val n = table.size / 2
        val x = (((t % LOOP) + LOOP) % LOOP) * TABLE_RATE
        val i = floor(x).toInt()
        val f = x - i
        val a = (i % n) * 2
        val b = ((i + 1) % n) * 2
        out[0] = mix(table[a], table[b], f)
        out[1] = mix(table[a + 1], table[b + 1], f)
    }

    /** One step of the touch-mode bubble spring on [position, velocity]. */
    fun stepBubble(state: DoubleArray, target: Double, dt: Double) {
        val steps = max(1, ceil(dt * 240).toInt())
        val h = dt / steps
        var x = state[0]
        var v = state[1]
        repeat(steps) {
            val a = -BUBBLE_OMEGA * BUBBLE_OMEGA * (x - target) - 2 * BUBBLE_ZETA * BUBBLE_OMEGA * v
            v += a * h
            x += v * h
        }
        state[0] = x
        state[1] = v
    }

    /** The roof's rise per 12 of run for a tilt (0 … 13). */
    fun riseOf(angle: Double) = 12 * tan(clamp(angle, 0.0, 50.0) * PI / 180)

    /** The pitch word as a continuous index, rolling over ±1.5° around each boundary. */
    fun pitchIndex(angle: Double): Double {
        var k = 0.0
        for (b in PITCH_BOUNDS) k += smooth(b - 1.5, b + 1.5, angle)
        return k
    }

    /** Rests on whole numbers; rolls to the next across the middle 30 %. */
    fun softRound(v: Double): Double {
        val f = floor(v)
        return f + smooth(0.35, 0.65, v - f)
    }

    /** Rubber band past the tilt limits. */
    fun rubber(angle: Double): Double {
        if (angle > MAX_ANGLE) return MAX_ANGLE + (angle - MAX_ANGLE) / (1 + (angle - MAX_ANGLE) / 8)
        if (angle < MIN_ANGLE) return MIN_ANGLE - (MIN_ANGLE - angle) / (1 + (MIN_ANGLE - angle) / 8)
        return angle
    }

    /** JavaScript's Math.round (halves round up). */
    fun jsRound(v: Double): Int = floor(v + 0.5).toInt()
}
