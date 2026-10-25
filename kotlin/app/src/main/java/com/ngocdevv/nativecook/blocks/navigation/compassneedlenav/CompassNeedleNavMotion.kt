package com.ngocdevv.nativecook.blocks.navigation.compassneedlenav

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Compass Needle Nav — the needle in its liquid and the pages swinging round the compass, ported
 from compassMotion.ts.

 The needle is an underdamped spring (ω 7 rad/s, ζ 0.4: it swings 25 % past a new heading and
 settles in about 1.4 s, as a needle floating in damping fluid does). The pages hang on a wheel
 centred on the compass, one every 90°; the wheel follows the same target critically damped
 (ω 5.5 rad/s), so a page swings in from the side its tab sits on, without wobbling.

 LOOP = 8 s: rest on N (Map) 0 – 1.2; to E (Trips) at 1.2, settled by 2.6; hold; to S (Gear) at
 3.4, settled by 4.8; hold; on round through W to N at 5.8 (a half turn), settled by 7.2; 7.4 – 7.9
 the last hundredth of a degree is eased out so 8.0 equals 0.0; rest.
*/
internal object CompassMotion {
    const val LOOP = 8.0
    const val NEEDLE_OMEGA = 7.0
    const val NEEDLE_ZETA = 0.4
    const val WHEEL_OMEGA = 5.5
    const val IDLE_BEFORE_BACK = 2.0
    const val BACK_FOR = 0.9

    /** [time, degrees added to the heading]. */
    private val STEPS = arrayOf(doubleArrayOf(1.2, 90.0), doubleArrayOf(3.4, 90.0), doubleArrayOf(5.8, 180.0))

    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun easeInOut(x: Double): Double {
        val v = clamp01(x)
        return if (v < 0.5) 4 * v * v * v else 1 - (-2 * v + 2).pow(3) / 2
    }

    /** Wraps degrees to -180 … 180 (halves round up, as JavaScript's Math.round does). */
    fun wrap(d: Double) = d - 360 * jsRound(d / 360)

    /** JavaScript's Math.round: halves go up. */
    fun jsRound(x: Double) = kotlin.math.floor(x + 0.5)

    private fun under(tau: Double): Double {
        if (tau <= 0) return 0.0
        val w = NEEDLE_OMEGA
        val z = NEEDLE_ZETA
        val wd = w * sqrt(1 - z * z)
        return 1 - exp(-z * w * tau) * (cos(wd * tau) + (z * w / wd) * sin(wd * tau))
    }

    private fun critical(tau: Double): Double {
        if (tau <= 0) return 0.0
        val w = WHEEL_OMEGA
        return 1 - exp(-w * tau) * (1 + w * tau)
    }

    private fun headingAt(t: Double, out: DoubleArray) {
        var needle = 0.0
        var wheel = 0.0
        var target = 0.0
        for (st in STEPS) {
            needle += st[1] * under(t - st[0])
            wheel += st[1] * critical(t - st[0])
            if (t >= st[0]) target += st[1]
        }
        // Ease out the spring's last residue so the loop closes exactly on 360°.
        val k = easeInOut((t - 7.4) / 0.5)
        out[0] = needle + (360 - needle) * k
        out[1] = wheel + (360 - wheel) * k
        out[2] = target
    }

    /** The pose of the self-playing loop at loop time t. */
    fun loopPose(t: Double): CompassPose {
        val h = DoubleArray(3)
        val h2 = DoubleArray(3)
        headingAt(t, h)
        headingAt(max(0.0, t - 1.0 / 60), h2)
        return CompassPose(h[0], h[1], (h[0] - h2[0]) * 60, Double.NaN, h[2])
    }

    /** The pose under (or after) a finger. */
    fun simPose(s: CompassSim, now: Double): CompassPose {
        if (s.backAt >= 0) {
            val k = easeInOut((now - s.backAt) / BACK_FOR)
            return CompassPose(s.fromNeedle + (s.target - s.fromNeedle) * k, s.fromWheel + (s.target - s.fromWheel) * k, 0.0, Double.NaN, s.target)
        }
        return CompassPose(s.needle, s.wheel, s.vel, s.finger, s.target)
    }
}

internal class CompassPose(
    /** Needle heading and the page wheel's angle, degrees (continuous, 0 = N, clockwise). */
    val needle: Double,
    val wheel: Double,
    /** Needle angular velocity, degrees per second (moves the bubble). */
    val spin: Double,
    /** The finger's angle on the bezel while dragging, or NaN. */
    val finger: Double,
    /** The heading being steered to (lights its mark on the bezel). */
    val target: Double,
)

/** The finger's simulation, stepped once per frame. */
internal class CompassSim {
    var needle = 0.0
    var vel = 0.0
    var wheel = 0.0
    var wheelVel = 0.0
    var target = 0.0
    var finger = Double.NaN
    /** Easing back: start time, and where it started from. */
    var backAt = -1.0
    var fromNeedle = 0.0
    var fromWheel = 0.0

    /** Steps both springs toward the target (fixed 1/240 s substeps, so it is stable at any rate). */
    fun step(dt: Double) {
        val n = max(1, ceil(dt * 240).roundToInt())
        val h = dt / n
        val w = CompassMotion.NEEDLE_OMEGA
        val z = CompassMotion.NEEDLE_ZETA
        val ww = CompassMotion.WHEEL_OMEGA
        repeat(n) {
            vel += (-w * w * (needle - target) - 2 * z * w * vel) * h
            needle += vel * h
            wheelVel += (-ww * ww * (wheel - target) - 2 * ww * wheelVel) * h
            wheel += wheelVel * h
        }
    }
}
