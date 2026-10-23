package com.ngocdevv.nativecook.blocks.media.iridescentcdflip

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** The block's clock: the disc's turn and the captions as functions of time. Ported from IridescentCdFlipMotion.swift. */
internal object CdMotion {
    const val LOOP = 8.0
    /** The two flips of the loop: to the data side, then on round to the printed side. */
    val flips = doubleArrayOf(1.2, 4.2)
    /** The camera's distance for the disc's perspective, in dp. */
    const val DEPTH = 1000.0

    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

    // The flip spring: ω 6.5, ζ 0.6 (about 9 % overshoot, a wobble on landing).
    private const val SPRING_A = 0.6 * 6.5
    private val springB = 6.5 * sqrt(1 - 0.6 * 0.6)

    /** A spring from x0 (velocity v0) to target: value and velocity after t. */
    fun spring(x0: Double, v0: Double, target: Double, t: Double): DoubleArray {
        val a = SPRING_A
        val b = springB
        val c1 = x0 - target
        val c2 = (v0 + a * c1) / b
        val e = exp(-a * t)
        val value = target + e * (c1 * cos(b * t) + c2 * sin(b * t))
        val velocity = e * ((-a * c1 + b * c2) * cos(b * t) + (-a * c2 - b * c1) * sin(b * t))
        return doubleArrayOf(value, velocity)
    }

    /** The idle sway: ±4° once a loop, so the light keeps sliding across the disc. */
    fun sway(t: Double) = 4 * sin(2 * PI * t / LOOP)

    /** The disc's turn in degrees at loop time t (0 printed side, 180 data side, 360 printed again). */
    fun loopAngle(t: Double): Double {
        // Each flip springs from the previous rest to the next; it has settled long before the next one.
        var angle = 0.0
        for (f in flips) if (t >= f) angle += 180 + spring(-180.0, 0.0, 0.0, t - f)[0]
        return angle + sway(t)
    }

    /** The side the loop is heading for at t (true: printed), and since when (the start of the last flip). */
    fun loopCaption(t: Double): Pair<Boolean, Double> {
        if (t >= flips[1]) return Pair(true, t - flips[1])
        if (t >= flips[0]) return Pair(false, t - flips[0])
        return Pair(true, 10.0)
    }

    /** Which side faces the viewer for a turn (degrees): true for the printed side. */
    fun printedSide(angle: Double) = cos(angle * PI / 180) >= 0

    private const val GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&*/<>"

    /** A small integer hash (PCG), the same in every stack: 0 to 1. */
    fun hash(x: Int, salt: Int): Double {
        var h = x * 747796405 + salt * -1403630843 + 1 // 2891336453 as a signed 32-bit int
        h = ((h ushr ((h ushr 28) + 4)) xor h) * 277803737
        h = (h ushr 22) xor h
        return (h.toLong() and 0xffffffffL).toDouble() / 4294967295.0
    }

    /**
     * [text] typed in over 0.45 s after a change: unresolved letters flicker through random glyphs
     * (a new one every 1/30 s), resolving left to right.
     */
    fun glitch(text: String, e: Double, salt: Int): String {
        if (e >= 0.45) return text
        val frame = (max(0.0, e) * 30).toInt()
        val out = StringBuilder()
        text.forEachIndexed { i, ch ->
            val resolve = 0.45 * i / max(1, text.length)
            if (e >= resolve || ch == ' ') out.append(ch)
            else out.append(GLYPHS[(hash(frame * 131 + i, salt) * GLYPHS.length).toInt() % GLYPHS.length])
        }
        return out.toString()
    }
}
