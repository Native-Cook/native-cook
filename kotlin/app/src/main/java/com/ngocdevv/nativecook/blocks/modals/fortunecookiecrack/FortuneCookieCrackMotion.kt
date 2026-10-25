package com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The fortune cookie's clock, ported from fortuneMotion.ts / FortuneCookieCrackMotion.swift. Every
 * value is a closed-form function of the loop time t, so the frame at t = LOOP is the frame at 0,
 * and a finger only moves t. Times are seconds, lengths dp (= pt), angles radians.
 */
internal object FortuneCookieCrackMotion {
    const val LOOP = 9.0

    const val SQUEEZE_AT = 1.4
    const val CRACK_AT = 2.0
    /** Where a quick tap starts playing (a shorter squeeze). */
    const val TAP_FROM = 1.72
    /** The slip is fully unrolled here; the finger's playback pauses on it. */
    const val OPEN_AT = 4.4
    const val CLOSE_AT = 6.4

    const val CRUMBS = 26
    private const val GRAVITY = 1100.0

    fun clamp01(x: Double) = min(1.0, max(0.0, x))
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    /** Ease-out that passes the target by about 5 % and settles back. */
    fun easeOutBack(x: Double): Double {
        val c = 1.1
        return 1 + (c + 1) * (x - 1).pow(3) + c * (x - 1).pow(2)
    }

    fun hash(i: Double): Double {
        val s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    class Pose(
        /** Idle bob (dp) and tilt (rad), only while resting. */
        val bob: Double,
        val tilt: Double,
        /** Squash of the squeeze. */
        val sx: Double,
        val sy: Double,
        /** Shake before the snap (rad). */
        val shake: Double,
        /** Crack lines drawn 0…1; how far the halves have opened (overshoots a little). */
        val crack: Double,
        val open: Double,
        /** Rolled slip: emerging, dropped to its place, unrolled (0…1 each). */
        val emerge: Double,
        val drop: Double,
        val unroll: Double,
        /** Seconds since the snap (< 0 before), and the crumbs' fade. */
        val crumbT: Double,
        val crumbA: Double,
        /** 0 closed … 1 open: drives the roll of the sub label. */
        val stage: Double,
    )

    /** The whole loop as a function of t in [0, LOOP). */
    fun cookieAt(t: Double): Pose {
        val rest = 1 - easeInOut(seg(t, 1.0, SQUEEZE_AT)) + easeInOut(seg(t, 8.4, 8.9))
        val w = min(1.0, rest)
        val squeeze = easeInOut(seg(t, SQUEEZE_AT, CRACK_AT)) * (1 - easeOut(seg(t, CRACK_AT, 2.25)))
        val shakeEnv = sin(PI * seg(t, 1.72, CRACK_AT))
        val crack = easeOut(seg(t, CRACK_AT, 2.45)) * (1 - easeInOut(seg(t, 8.0, 8.4)))
        // A hairline gap as the crack runs, then the snap: halves swing apart and settle.
        val pre = 0.06 * easeOut(seg(t, 2.1, 2.5))
        val open = (pre + (1 - pre) * easeOutBack(seg(t, 2.5, 3.2))) * (1 - easeInOut(seg(t, 7.4, 8.0)))
        val emerge = easeOut(seg(t, 3.15, 3.5)) * (1 - easeInOut(seg(t, 7.25, 7.5)))
        val drop = easeInOut(seg(t, 3.45, 3.85)) * (1 - easeInOut(seg(t, 6.95, 7.35)))
        val unroll = easeInOut(seg(t, 3.8, OPEN_AT)) * (1 - easeInOut(seg(t, CLOSE_AT, 7.0)))
        val stage = easeInOut(seg(t, 3.3, 3.7)) * (1 - easeInOut(seg(t, 6.6, 7.0)))
        return Pose(
            bob = 3 * sin(2 * PI * 4 * t / LOOP) * w,
            tilt = 0.014 * sin(2 * PI * 2 * t / LOOP + 0.6) * w,
            sx = 1 + 0.05 * squeeze,
            sy = 1 - 0.065 * squeeze,
            shake = 0.022 * shakeEnv * sin(2 * PI * 81 * t / LOOP),
            crack = crack,
            open = open,
            emerge = emerge,
            drop = drop,
            unroll = unroll,
            crumbT = if (t >= 2.5 && t < 5) t - 2.5 else -1.0,
            crumbA = 1 - seg(t, 4.2, 4.8),
            stage = stage,
        )
    }

    /** Lucky number chip i: 0 hidden … 1 shown (with a pop). */
    fun chipAt(t: Double, i: Int): Double {
        val a = 4.05 + 0.06 * i
        return easeOutBack(seg(t, a, a + 0.36)) * (1 - easeInOut(seg(t, CLOSE_AT, CLOSE_AT + 0.3)))
    }

    /**
     * Crumb i, tau seconds after the snap, in cookie-local coordinates with [floor] the plate's
     * surface: x, y, radius, and whether it has left yet (in [out]).
     */
    fun crumbAt(i: Int, tau: Double, floor: Double, out: DoubleArray): Boolean {
        val sx = (hash(i * 3.1) - 0.5) * 14
        val sy = -30 + hash(i * 5.7) * 90
        val side = if (hash(i * 1.3) < 0.5) -1 else 1
        val vx = side * (40 + 160 * hash(i * 7.9))
        val vy = -120 - 260 * hash(i * 2.3)
        val delay = 0.08 * hash(i * 4.4)
        val tt = max(0.0, tau - delay)
        var x = sx + vx * tt
        var y = sy + vy * tt + 0.5 * GRAVITY * tt * tt
        val land = floor - 4 + 12 * hash(i * 6.6)
        if (y > land) {
            // Time it reached the plate: from there it slides a little and stops.
            val hit = (-vy + sqrt(vy * vy + 2 * GRAVITY * (land - sy))) / GRAVITY
            x = sx + vx * hit + vx * 0.06 * (1 - exp(-(tt - hit) * 8))
            y = land
        }
        out[0] = x
        out[1] = y
        out[2] = 1.1 + 2.1 * hash(i * 8.8)
        return tau >= delay
    }
}
