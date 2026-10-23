package com.ngocdevv.nativecook.blocks.media.vinylswapscratch

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The block's clock, ported from VinylSwapScratchMotion.swift: every value on screen is a function
 * of time, in closed form. Angles are degrees, clockwise positive; times are seconds.
 */
internal object VinylMotion {
    const val LOOP = 10.0
    /** About 33⅓ rpm; the real speed is rounded so the platter turns a whole number of times a loop. */
    private const val NOMINAL_SPEED = 200.0

    // The scripted scratch.
    const val STOP_AT = 1.5
    const val STOP_FOR = 0.12
    private val strokes = arrayOf(doubleArrayOf(1.62, 0.6, 55.0), doubleArrayOf(2.22, 0.5, 75.0), doubleArrayOf(2.72, 0.44, 95.0))
    const val RELEASE_AT = 3.2

    /** The two record changes: A to B, then B back to A (start, from, to). */
    val swaps = arrayOf(Triple(4.6, 0, 1), Triple(7.7, 1, 0))
    const val SWAP_LENGTH = 1.8

    /** Waveform bars that pass the playhead per turn of the platter. */
    const val BARS_PER_TURN = 18.0
    /** Every ninth bar is a kick (twice a turn). */
    private const val KICK_EVERY = 9

    // The motor: a spring of ω 12, ζ 0.45 on the platter's speed (about 20 % overshoot).
    private const val MOTOR_A = 0.45 * 12
    private val motorB = 12 * sqrt(1 - 0.45 * 0.45)

    /** e^(−at)(cos bt + a/b sin bt): 1 at 0, decaying to 0 with an overshoot. */
    fun motorResponse(t: Double) = exp(-MOTOR_A * t) * (cos(motorB * t) + MOTOR_A / motorB * sin(motorB * t))

    private fun motorF(x: Double): Double {
        val a = MOTOR_A
        val b = motorB
        return exp(-a * x) * ((b - a * a / b) * sin(b * x) - 2 * a * cos(b * x)) / (a * a + b * b)
    }

    /** ∫₀ᵗ motorResponse. */
    fun motorIntegral(t: Double) = motorF(t) - motorF(0.0)

    /** The motor pulling the platter from speed v0 to [target]: angle travelled and speed after t. */
    fun approach(v0: Double, target: Double, t: Double) =
        doubleArrayOf(target * t + (v0 - target) * motorIntegral(t), target + (v0 - target) * motorResponse(t))

    /** The platter braking from v0 to rest over [duration] (speed falls as (1 − u)²). */
    fun brake(v0: Double, duration: Double, t: Double): DoubleArray {
        val u = (t / duration).coerceIn(0.0, 1.0)
        return doubleArrayOf(v0 * duration * (1 - (1 - u).pow(3)) / 3, v0 * (1 - u) * (1 - u))
    }

    /** The loop's platter, in units of the motor speed: ∫ speed and speed. */
    fun shape(t: Double): DoubleArray {
        if (t < STOP_AT) return doubleArrayOf(t, 1.0)
        if (t < RELEASE_AT) {
            val b = brake(1.0, STOP_FOR, t - STOP_AT)
            return doubleArrayOf(STOP_AT + b[0], b[1])
        }
        val r = approach(0.0, 1.0, t - RELEASE_AT)
        return doubleArrayOf(STOP_AT + STOP_FOR / 3 + r[0], r[1])
    }

    /** Whole turns of the platter per loop. */
    val turns: Int = (NOMINAL_SPEED * shape(LOOP)[0] / 360).roundToInt()
    /** The motor speed, in degrees a second. */
    val omega: Double = turns * 360.0 / shape(LOOP)[0]
    /** Bars before the waveform repeats: one loop's worth. */
    val period: Int = BARS_PER_TURN.toInt() * turns

    /** The scripted hand: three back-and-forth strokes, pulling the record back and pushing it on. */
    fun scratch(t: Double): DoubleArray {
        for (s in strokes) {
            if (t >= s[0] && t < s[0] + s[1]) {
                val u = (t - s[0]) / s[1]
                return doubleArrayOf(-s[2] * (1 - cos(2 * PI * u)) / 2, -s[2] * PI / s[1] * sin(2 * PI * u))
            }
        }
        return doubleArrayOf(0.0, 0.0)
    }

    // Curves.

    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    fun easeInOut(x: Double): Double {
        val t = clamp01(x)
        return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
    }
    fun easeIn(x: Double) = clamp01(x).pow(3)
    fun easeOut(x: Double) = 1 - (1 - clamp01(x)).pow(3)

    /** A spring step from 0 to 1 (ω 11, ζ 0.62): the new record lands with a small overshoot. */
    fun landing(t: Double): Double {
        val w = 11.0
        val z = 0.62
        val a = z * w
        val b = w * sqrt(1 - z * z)
        return 1 - exp(-a * t) * (cos(b * t) + a / b * sin(b * t))
    }

    /** What the screen shows at a moment. */
    class Pose {
        /** The platter's angle and speed (degrees, degrees a second). */
        var angle = 0.0
        var speed = 0.0
        /** Each record's place: 0 on the platter, −1 gone off the left edge, 1 waiting off the right. */
        val discX = doubleArrayOf(0.0, 1.0)
        val discAlpha = doubleArrayOf(1.0, 0.0)
        /** 0 with the stylus in the groove, 1 swung out beside the record. */
        var arm = 0.0
        /** 0 down, 1 raised (a bigger, softer shadow). */
        var lift = 0.0
        /** Colours: 0 album A, 1 album B. */
        var mix = 0.0
        var waveMix = 0.0
        /** The waveform's loudness: low while the stylus is out of the groove. */
        var needle = 1.0
        val titleAlpha = doubleArrayOf(1.0, 0.0)
        val titleX = doubleArrayOf(0.0, 0.0)
        /** The scripted finger on the record: opacity, scale and how far it has turned the record. */
        var fingerAlpha = 0.0
        var fingerScale = 1.0
        var fingerTurn = 0.0
        /** The pills pressed by the script: pause, previous, next. */
        val press = doubleArrayOf(0.0, 0.0, 0.0)
        /** The time used for flickers. */
        var clock = 0.0

        companion object {
            fun rest(album: Int) = Pose().apply {
                discX[0] = if (album == 0) 0.0 else 1.0
                discX[1] = if (album == 0) 1.0 else 0.0
                discAlpha[0] = if (album == 0) 1.0 else 0.0
                discAlpha[1] = if (album == 0) 0.0 else 1.0
                mix = album.toDouble()
                waveMix = album.toDouble()
                titleAlpha[0] = discAlpha[0]
                titleAlpha[1] = discAlpha[1]
            }
        }
    }

    /** A record change [e] seconds in. Reduced motion: a 0.3 s cross-fade, nothing moves. */
    fun swap(p: Pose, e: Double, from: Int, to: Int, still: Boolean) {
        val dir = if (to > from) 1.0 else -1.0
        fun toward(m: Double) = from + dir * m
        if (still) {
            val a = smoothstep(0.0, 0.3, e)
            p.discX[from] = 0.0; p.discX[to] = 0.0
            p.discAlpha[from] = 1 - a; p.discAlpha[to] = a
            p.mix = toward(a); p.waveMix = toward(a)
            p.titleAlpha[from] = 1 - a; p.titleAlpha[to] = a
            p.titleX[0] = 0.0; p.titleX[1] = 0.0
            return
        }
        p.arm = if (e < 1.3) easeInOut(e / 0.4) else 1 - easeOut((e - 1.3) / 0.5)
        p.lift = smoothstep(0.0, 0.25, e) * (1 - smoothstep(1.55, 1.8, e))
        p.needle = clamp01(1 - smoothstep(0.05, 0.3, e) + smoothstep(1.6, 1.8, e))
        p.discX[from] = if (e < 0.3) 0.0 else -easeIn((e - 0.3) / 0.5)
        p.discX[to] = if (e < 0.6) 1.0 else if (e >= 1.5) 0.0 else 1 - landing(e - 0.6)
        p.discAlpha[from] = 1.0; p.discAlpha[to] = 1.0
        p.mix = toward(smoothstep(0.4, 1.4, e))
        p.waveMix = toward(smoothstep(0.6, 1.3, e))
        val out = smoothstep(0.45, 0.75, e)
        p.titleAlpha[from] = 1 - out
        p.titleX[from] = -14 * out
        p.titleAlpha[to] = smoothstep(0.7, 1.05, e)
        p.titleX[to] = 14 * (1 - easeOut((e - 0.7) / 0.35))
    }

    /** A scripted press of a pill around time [at]. */
    private fun bump(t: Double, at: Double) = smoothstep(at - 0.18, at - 0.05, t) * (1 - smoothstep(at + 0.05, at + 0.25, t))

    /** The loop at time t (0 ≤ t < LOOP). */
    fun loopPose(t: Double): Pose {
        var p = Pose.rest(0)
        val s = shape(t)
        val k = scratch(t)
        val angle = omega * s[0] + k[0]
        val speed = omega * s[1] + k[1]
        val a = swaps[0]
        val b = swaps[1]
        if (t >= a.first && t < a.first + SWAP_LENGTH) {
            swap(p, t - a.first, a.second, a.third, false)
        } else if (t >= a.first + SWAP_LENGTH && t < b.first) {
            p = Pose.rest(1)
        } else if (t >= b.first && t < b.first + SWAP_LENGTH) {
            swap(p, t - b.first, b.second, b.third, false)
        }
        p.angle = angle
        p.speed = speed
        p.clock = t
        p.fingerAlpha = smoothstep(1.3, 1.5, t) * (1 - smoothstep(RELEASE_AT, RELEASE_AT + 0.2, t))
        p.fingerScale = 1.25 - 0.25 * smoothstep(1.3, 1.5, t) + 0.15 * smoothstep(RELEASE_AT, RELEASE_AT + 0.2, t)
        p.fingerTurn = k[0]
        p.press[0] = 0.0
        p.press[1] = bump(t, b.first)
        p.press[2] = bump(t, a.first)
        return p
    }

    /** Which record is on the platter (or leaving it) at loop time t: album, and the change in progress or null. */
    fun loopState(t: Double): Pair<Int, Triple<Double, Int, Int>?> {
        for (s in swaps) if (t >= s.first && t < s.first + SWAP_LENGTH) return Pair(s.second, s)
        return Pair(if (t >= swaps[0].first && t < swaps[1].first) 1 else 0, null)
    }

    /** A small integer hash (PCG), the same in every stack: 0 to 1. */
    fun hash(x: Int, salt: Int): Double {
        var h = x * 747796405 + salt * -1403630843 + 1 // 2891336453 as a signed 32-bit int
        h = ((h ushr ((h ushr 28) + 4)) xor h) * 277803737
        h = (h ushr 22) xor h
        return (h.toLong() and 0xffffffffL).toDouble() / 4294967295.0
    }

    /** Bar k's height (0 to 1) in [album]'s waveform; repeats every [period] bars. */
    fun bar(album: Int, k: Int): Double {
        val i = ((k % period) + period) % period
        if (i % KICK_EVERY == 0) return 0.95
        val x = i.toDouble() / period
        val seed = if (album == 0) 0.0 else 2.1
        val env = 0.5 + 0.22 * sin(2 * PI * 2 * x + seed) + 0.14 * sin(2 * PI * 5 * x + seed * 1.7)
        val h = env * (0.45 + 0.55 * hash(i, album + 1))
        return h.coerceIn(0.1, 0.88)
    }

    fun isKick(k: Int) = ((k % period) + period) % period % KICK_EVERY == 0

    /** How strongly the record is being pushed: drives the flare, the smear and the rim arcs. */
    fun intensity(speed: Double) = clamp01((abs(speed) - 250) / 350)
}
