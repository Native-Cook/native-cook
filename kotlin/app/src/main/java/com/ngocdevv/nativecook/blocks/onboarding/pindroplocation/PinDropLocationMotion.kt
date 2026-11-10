package com.ngocdevv.nativecook.blocks.onboarding.pindroplocation

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** The 402 x 874 reference-space layout used by the React Native reference. */
internal data class PinDropLocationLayout(
    val w: Double,
    val h: Double,
    val top: Double,
    val bottom: Double,
) {
    val sx = w / 402.0
    val k = ((h - top - bottom) / (874.0 - 62.0 - 34.0)).coerceAtLeast(0.01)

    fun y(reference: Double): Double =
        if (reference < 62.0) reference * top / 62.0 else top + (reference - 62.0) * k

    val discX = 201.0 * sx
    val discY = y(333.0)
    val discR = 92.0 * sx
    val pinTipY = discY + 0.49 * discR
    val skipX = 312.0 * sx
    val skipY = y(73.0)
    val skipW = 69.0
    val skipH = 42.0
    val titleY = y(482.0)
    val bodyY = y(539.0)
    val bodyW = 380.0 * sx
    val allowX = 20.0 * sx
    val allowY = y(763.0)
    val allowW = 362.0 * sx
    val allowH = 59.0 * k
    val mapTouchR = discR + 20.0
}

/**
 * Pure closed-form timeline shared by the Android renderer and its gesture preview.
 * It is intentionally a direct port of pdl-motion.ts; no frame depends on prior frames.
 */
internal object PinDropLocationMotion {
    const val LOOP = 10.0
    const val TURN = 1.5

    data class Frame(
        val time: Double,
        val bend: Double,
        val lon: Double,
        val lat: Double,
        val hover: Double,
        val fall: Double,
        val since: Double,
        val lights: Double,
        val zoom: Double,
        val places: Double,
        val allowed: Double,
        val finger: Double,
        val fingerAt: Double,
        val fingerU: Double,
        val press: Double,
    )

    fun clamp01(value: Double) = value.coerceIn(0.0, 1.0)

    fun seg(time: Double, start: Double, end: Double): Double {
        if (end <= start) return if (time >= end) 1.0 else 0.0
        return clamp01((time - start) / (end - start))
    }

    fun easeInOut(value: Double): Double {
        val p = clamp01(value)
        return if (p < 0.5) 4.0 * p * p * p else 1.0 - (-2.0 * p + 2.0).pow(3.0) / 2.0
    }

    fun easeOut(value: Double): Double {
        val u = 1.0 - clamp01(value)
        return 1.0 - u * u * u
    }

    fun easeIn(value: Double): Double {
        val p = clamp01(value)
        return p * p * p
    }

    /** The small landing squash, copied from the RN worklet. */
    fun squashAt(tau: Double): Double {
        if (tau <= 0.0 || tau >= 0.5) return 0.0
        val x = tau / 0.5
        return 0.22 * minOf(1.0, x / 0.08) * kotlin.math.exp(-5.5 * x) * kotlin.math.cos(12.0 * x) * (1.0 - x)
    }

    /** Cubic-bezier y for the Android timing tweens used by the RN port. */
    fun bezier(value: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val x = clamp01(value)
        var lo = 0.0
        var hi = 1.0
        repeat(28) {
            val t = (lo + hi) * 0.5
            val one = 1.0 - t
            val curve = 3.0 * one * one * t * x1 + 3.0 * one * t * t * x2 + t * t * t
            if (curve < x) lo = t else hi = t
        }
        val t = (lo + hi) * 0.5
        val one = 1.0 - t
        return 3.0 * one * one * t * y1 + 3.0 * one * t * t * y2 + t * t * t
    }

    /**
     * Returns the same normalized state as frameAt in pdl-motion.ts. The modulo makes the
     * endpoint exactly equal to the first frame, which keeps a recorded loop seam-free.
     */
    fun frameAt(rawTime: Double): Frame {
        var time = rawTime % LOOP
        if (time < 0.0) time += LOOP

        val bend = easeInOut(seg(time, 0.5, 1.1)) * (1.0 - easeInOut(seg(time, 2.6, 3.1)))
        val turn = easeInOut(seg(time, 1.1, 2.6))
        val home = easeInOut(seg(time, 8.0, 9.4))
        val lon = TURN * turn * (1.0 - home)
        val lat = 0.35 * sin(PI * turn) * (1.0 - home)
        val hover = if (time < 3.15) easeOut(seg(time, 0.5, 1.1)) else 0.0

        val fall = when {
            time >= 0.5 && time < 3.15 -> 1.0 - easeIn(seg(time, 0.6, 1.3))
            time >= 3.15 && time < 3.55 -> easeIn(seg(time, 3.15, 3.55))
            else -> 1.0
        }
        val since = if (time >= 3.55) time - 3.55 else -1.0
        val lights = 1.0 - easeInOut(seg(time, 8.6, 9.4))
        val zoom = easeInOut(seg(time, 5.5, 6.3)) * (1.0 - easeInOut(seg(time, 8.0, 8.8)))
        val places = easeOut(seg(time, 6.0, 6.8)) * (1.0 - easeInOut(seg(time, 8.0, 8.4)))
        val allowed = if (time >= 5.4 && time < 8.3) 1.0 else 0.0

        var finger = 0.0
        var fingerAt = 0.0
        var fingerU = 0.0
        var press = 0.0
        val drag = easeOut(seg(time, 0.32, 0.48)) * (1.0 - easeOut(seg(time, 2.6, 2.76)))
        if (drag > finger) {
            finger = drag
            fingerU = if (time < 1.1) -1.0 + easeInOut(seg(time, 0.5, 1.1)) else turn
            press = drag
        }
        val allow = easeOut(seg(time, 5.22, 5.36)) * (1.0 - easeOut(seg(time, 5.54, 5.7)))
        if (allow > finger) {
            finger = allow
            fingerAt = 1.0
            press = easeOut(seg(time, 5.36, 5.45)) * (1.0 - easeOut(seg(time, 5.5, 5.62)))
        }
        return Frame(time, bend, lon, lat, hover, fall, since, lights, zoom, places, allowed, finger, fingerAt, fingerU, press)
    }
}
