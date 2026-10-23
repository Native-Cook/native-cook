package com.ngocdevv.nativecook.blocks.media.bubbleringrecorder

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Everything that moves in the recorder is a function of a few times, so the loop and a finger
 * share the same drawing. All times in seconds. A port of BubbleRingRecorderMotion.swift.
 */
internal object BubbleRingMotion {
    /** autoPlay: tap at 1.2 s, record for 5.2 s, stop at 6.4 s, settled by 7.4 s, rest to 9 s. */
    const val LOOP = 9.0
    const val RECORD_AT = 1.2
    const val TAKE = 5.2
    const val STOP_AT = RECORD_AT + TAKE
    /** The orb's glow breathes four times a loop. */
    const val BREATH = 2.25
    /** Collapse of the orb into the dot, and the ring's arrival. */
    const val COLLAPSE = 0.5
    const val RING_IN = 0.45
    const val RING_OUT = 0.6
    /** The red dot while recording. */
    const val DOT_RADIUS = 9.0
    /** The grey ghost of the outline trails the white one by this much. */
    const val GHOST_LAG = 0.22

    fun clamp(x: Double, a: Double = 0.0, b: Double = 1.0) = min(max(x, a), b)

    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = clamp((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }

    fun easeInOut(x: Double): Double {
        val t = clamp(x)
        return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
    }

    fun easeOut(x: Double): Double {
        val t = clamp(x)
        return 1 - (1 - t).pow(3)
    }

    /** A spring from 0 to 1 (stiffness 170, damping 16, mass 1): about 8 % overshoot, settled in 0.8 s. */
    fun spring(t: Double): Double {
        if (t <= 0) return 0.0
        val w0 = sqrt(170.0)
        val zeta = 16 / (2 * w0)
        val wd = w0 * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w0 * t) * (cos(wd * t) + zeta * w0 / wd * sin(wd * t))
    }

    /** The jelly kick the ring gets when a take stops: up, down, gone in about a second. */
    fun kick(u: Double) = if (u <= 0) 0.0 else exp(-4.5 * u) * sin(11 * u)

    /** 0…1, four times a loop; 0 at the loop's start. */
    fun breathing(clock: Double) = 0.5 - 0.5 * cos(2 * PI * clock / BREATH)

    /** Syllables of the scripted voice: centre, height, width (s). */
    private val syllables = arrayOf(
        doubleArrayOf(0.75, 0.55, 0.16), doubleArrayOf(1.05, 0.85, 0.2), doubleArrayOf(1.5, 0.6, 0.14),
        doubleArrayOf(1.95, 1.0, 0.22), doubleArrayOf(2.35, 0.7, 0.16), doubleArrayOf(3.0, 0.5, 0.14),
        doubleArrayOf(3.3, 0.9, 0.2), doubleArrayOf(3.75, 0.75, 0.18), doubleArrayOf(4.3, 0.6, 0.16),
    )

    /**
     * The input level s seconds into a take, 0…1. It repeats every [TAKE] seconds, so a longer take
     * made by hand keeps talking; it is at its quiet floor at both ends of the pattern.
     */
    fun level(s: Double): Double {
        if (s < 0) return 0.0
        val p = s - floor(s / TAKE) * TAKE
        var v = 0.12
        for (syl in syllables) {
            val d = (p - syl[0]) / syl[2]
            v += syl[1] * exp(-d * d)
        }
        // A fast tremble so the voice never sounds like a sine.
        v *= 1 + 0.12 * sin(37 * s) + 0.08 * sin(23 * s + 1)
        return clamp(v) * smoothstep(0.1, 0.5, s)
    }

    /** The ring's shape at angle θ: a few low harmonics drifting at their own speeds, -1…1. */
    fun shape(theta: Double, s: Double) =
        0.5 * sin(2 * theta + 1.7 * s + 0.4) + 0.3 * sin(3 * theta - 2.3 * s + 1.1) +
            0.2 * sin(5 * theta + 3.1 * s + 2.0) + 0.12 * sin(7 * theta - 4.2 * s)

    /** When the take being shown started and stopped (seconds ago, null when not). */
    class Clock(val sinceStart: Double?, val sinceStop: Double?, val lastTake: Double, val clock: Double)

    class Frame(
        var orbRadius: Double,
        var orbness: Double,
        var presence: Double = 0.0,
        var ringScale: Double = 1.0,
        var wobble: Double = 0.0,
        var ghostWobble: Double = 0.0,
        var shapeTime: Double = 0.0,
        var level: Double = 0.0,
        var glow: Double = 0.0,
        var ripple: Double? = null,
        var rippleIsStop: Boolean = false,
        var seconds: Double = 0.0,
        var recording: Double = 0.0,
    )

    /** The radius of the orb a recording started s seconds ago, before it stops. */
    fun recordingOrb(s: Double, r: Double, reduced: Boolean): Double {
        if (reduced) return DOT_RADIUS
        val c = easeInOut(s / COLLAPSE)
        val dot = DOT_RADIUS * (1 + 0.35 * level(s))
        return r + (dot - r) * c
    }

    fun frame(k: Clock, r: Double, reduced: Boolean): Frame {
        val b = if (reduced) 0.0 else breathing(k.clock)
        val f = Frame(orbRadius = r * (1 + 0.012 * b), orbness = 1.0, glow = b)
        val s = k.sinceStart ?: return f
        val u = k.sinceStop
        if (u != null) {
            // Stopping: the orb springs back out of the dot, the ring jiggles and fades to the faint circle.
            val len = s - u
            val from = recordingOrb(len, r, reduced)
            val presAtStop = if (reduced) 1.0 else easeInOut(len / RING_IN)
            if (reduced) {
                val x = clamp(u / 0.25)
                f.orbRadius = r
                f.orbness = x
                f.presence = presAtStop * (1 - x)
                return f
            }
            f.orbRadius = from + (r * (1 + 0.012 * b) - from) * spring(u)
            f.orbness = smoothstep(DOT_RADIUS * 2, r, f.orbRadius)
            f.presence = presAtStop * (1 - easeInOut(u / RING_OUT))
            f.ringScale = 1 - 0.07 * kick(u)
            f.wobble = 0.08 * kick(u)
            f.ghostWobble = 0.08 * kick(u - GHOST_LAG)
            f.shapeTime = len
            f.glow = b * smoothstep(0.3, 1.0, u)
            f.ripple = if (u < 0.6) u / 0.6 else null
            f.rippleIsStop = true
            f.seconds = 0.0
            return f
        }
        // Recording.
        val lv = level(s)
        f.level = lv
        f.recording = 1.0
        f.seconds = s
        f.orbRadius = recordingOrb(s, r, reduced)
        f.orbness = if (reduced) 0.0 else smoothstep(DOT_RADIUS * 2, r, f.orbRadius)
        f.presence = if (reduced) 1.0 else easeInOut(s / RING_IN)
        if (!reduced) {
            f.ringScale = 1 + 0.05 * lv
            f.wobble = 0.14 * lv * f.presence
            f.ghostWobble = 0.14 * level(s - GHOST_LAG) * f.presence
            f.shapeTime = s
            f.ripple = if (s < 0.7) s / 0.7 else null
        }
        f.glow = 0.0
        return f
    }

    /** Clock for autoPlay at loop time e. */
    fun autoClock(e: Double, clock: Double, lastTake: Double, afterFirstStop: Boolean): Clock {
        if (e >= STOP_AT) return Clock(e - RECORD_AT, e - STOP_AT, TAKE, clock)
        val last = if (afterFirstStop) TAKE else lastTake
        if (e >= RECORD_AT) return Clock(e - RECORD_AT, null, last, clock)
        return Clock(null, null, last, clock)
    }

    /**
     * The take's waveform for the list row: n bars spread over 5.2 s, filled left to right while a
     * take of [length] seconds grows (a longer one scrolls); bars not recorded yet are null.
     */
    fun bars(n: Int, length: Double): Array<Double?> {
        val from = max(0.0, length - TAKE)
        return Array(n) { i ->
            val t = from + i.toDouble() / (n - 1) * TAKE
            if (t <= length + 0.0001) level(t) else null
        }
    }

    /** A recording's static waveform, for the older rows. */
    fun staticBar(j: Int, seed: Double): Double {
        val x = j.toDouble()
        return 0.15 + 0.85 * abs(sin(x * 0.61 + seed)) * (0.35 + 0.65 * sin(x * 0.23 + seed * 2).pow(2))
    }
}
