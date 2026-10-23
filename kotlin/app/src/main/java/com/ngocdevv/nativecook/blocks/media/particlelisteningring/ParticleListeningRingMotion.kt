package com.ngocdevv.nativecook.blocks.media.particlelisteningring

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The particles of the listening ring. Every value is a function of the loop time and a few seeded
 * numbers per particle, so the loop is seamless and the stacks draw the same ring. A port of
 * ParticleListeningRingMotion.swift.
 */
internal object ListeningMotion {
    const val LOOP = 6.0
    /** Particles on the ring, and particles that fall into the middle when the voice is loud. */
    const val RING_COUNT = 96
    const val INNER_COUNT = 16
    const val COUNT = RING_COUNT + INNER_COUNT

    /** Palette: coral, violet, teal, sun. */
    val colors = longArrayOf(0xFFFF6B57, 0xFF6C4CF5, 0xFF19B39A, 0xFFFFB020)

    private class Burst(val c: Double, val w: Double, val h: Double, val mix: DoubleArray)

    /**
     * Three swells of the scripted voice: centre (s), width (s), height, and how much each colour
     * leads it (violet, then teal with sun, then coral).
     */
    private val bursts = arrayOf(
        Burst(1.1, 0.42, 0.9, doubleArrayOf(0.1, 1.0, 0.15, 0.1)),
        Burst(2.45, 0.5, 1.0, doubleArrayOf(0.1, 0.1, 1.0, 0.8)),
        Burst(3.85, 0.52, 0.9, doubleArrayOf(1.0, 0.15, 0.1, 0.2)),
    )

    fun clamp(x: Double, a: Double = 0.0, b: Double = 1.0) = min(max(x, a), b)

    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = clamp((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }

    fun easeOut(x: Double): Double {
        val t = clamp(x)
        return 1 - (1 - t).pow(3)
    }

    /** A seeded number in 0..<1 for particle i and channel k (the same in every stack). */
    fun rnd(i: Int, k: Int): Double {
        val v = sin(i * 12.9898 + k * 78.233) * 43758.5453
        return v - floor(v)
    }

    /** 2π · cycles · t / loop: a phase that comes back to itself at the end of the loop. */
    fun wave(cycles: Double, t: Double) = 2 * PI * cycles * t / LOOP

    /** The voice's envelope: silent at both ends of the loop. */
    fun window(t: Double) = smoothstep(0.3, 0.8, t) * (1 - smoothstep(4.7, 5.4, t))

    /** A quick flutter on the level, so the swells sound like a voice. */
    fun flutter(t: Double) = 1 + 0.18 * sin(wave(11.0, t)) + 0.1 * sin(wave(17.0, t) + 1)

    private fun bump(b: Burst, t: Double): Double {
        val d = (t - b.c) / b.w
        return b.h * exp(-d * d)
    }

    /** The scripted level at loop time t, 0…~1. */
    fun level(t: Double): Double {
        var a = 0.0
        for (b in bursts) a += bump(b, t)
        return a * window(t) * flutter(t)
    }

    /** How much a particle of colour c grows from the scripted voice at loop time t. */
    fun growth(c: Int, t: Double): Double {
        var g = 0.0
        for (b in bursts) g += bump(b, t) * (0.25 + 0.75 * b.mix[c])
        return g * window(t) * flutter(t)
    }

    /** Weights of the four colours for a finger at angle a: the colour of that quarter leads. */
    fun touchMix(a: Double, c: Int): Double {
        val q = a / (2 * PI) + 1
        val quarter = (q - floor(q)) * 4
        var d = abs(quarter - (c + 0.5))
        d = min(d, 4 - d)
        return clamp(1.2 - d)
    }

    /** Everything that drives the ring at a moment. */
    class Drive(val t: Double, val gain: Double, val touch: Double, val touchAngle: Double)

    /** The colour that leads at a moment, as weights of the four (for the glow behind the ring). */
    fun lead(d: Drive, c: Int) = d.gain * growth(c, d.t) + d.touch * (0.25 + 0.75 * touchMix(d.touchAngle, c))

    /** Writes particle i into [out]: x, y, radius, alpha, colour. */
    fun dot(i: Int, d: Drive, cx: Double, cy: Double, rr: Double, out: DoubleArray) {
        val t = d.t
        val inner = i >= RING_COUNT
        val theta = if (inner) 2 * PI * rnd(i, 1) else 2 * PI * (i + 0.6 * (rnd(i, 1) - 0.5)) / RING_COUNT
        val c = (rnd(i, 2) * 4).toInt() % 4
        val restSize = 1.0 + 1.4 * rnd(i, 3)
        val bigSize = 2.6 + 4.6 * rnd(i, 4).pow(1.6)
        val k = 2.0 + (rnd(i, 5) * 4).toInt()
        val phi = 2 * PI * rnd(i, 6)
        val j = rnd(i, 7) - 0.5
        val j2 = rnd(i, 8) * 2 - 1

        val a = d.gain * level(t) + d.touch
        val swell = 1 + 0.12 * min(a, 1.2)
        val n = 0.6 * sin(3 * theta + wave(2.0, t) + 0.3) + 0.4 * sin(5 * theta - wave(3.0, t) + 1.7)
        val angle = theta + 0.18 * sin(wave(1.0, t)) + 0.05 * sin(wave(k, t) + phi)

        // The finger pushes the dots near it outward.
        var da = angle - d.touchAngle
        da = atan2(sin(da), cos(da))
        val push = d.touch * exp(-(da / 0.6) * (da / 0.6))

        var r = rr * (1 + 0.03 * j) * swell + rr * min(a, 1.2) * (0.16 * n + 0.12 * j2) + rr * 0.22 * push
        if (inner) {
            val fall = smoothstep(0.2, 0.8, a) * (0.75 - 0.5 * rnd(i, 9))
            r *= 1 - fall
        }
        val pulse = 0.75 + 0.25 * sin(wave(k, t) + phi)
        val grow = d.gain * growth(c, t) + d.touch * (0.25 + 0.75 * touchMix(d.touchAngle, c))
        val size = restSize * (1 - 0.35 * min(a, 1.0)) * (1 + 0.15 * sin(wave(k, t) + phi)) + grow * bigSize * pulse

        // In the quiet dips of the voice, half the ring empties, the empty half turning once a loop.
        val quiet = d.gain * window(t) * (1 - smoothstep(0.15, 0.55, level(t)))
        val sector = smoothstep(-0.1, 0.5, sin(angle - wave(1.0, t) - 0.6))
        val alpha = 1 - 0.9 * quiet * sector

        out[0] = cx + r * cos(angle)
        out[1] = cy + r * sin(angle)
        out[2] = size * (0.2 + 0.8 * alpha)
        out[3] = alpha
        out[4] = c.toDouble()
    }
}
