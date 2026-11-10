package com.ngocdevv.nativecook.blocks.cards.smokedclochereveal

import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.PI

/** The November 2026 reference's ten second script, in seconds and device points. */
internal object SmokedClocheRevealMotion {
    const val LOOP = 10.0
    const val SEAL = 0.14
    const val REFILL = 8.7
    const val LAND_AT = 0.72
    const val LAND_HOP = 0.03

    fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
    fun out(x: Double) = 1 - (1 - x).pow(3)
    fun inside(x: Double) = x * x * x
    fun io(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun setDown(p: Double): Double {
        if (p <= 0) return 1.0
        if (p >= 1) return 0.0
        if (p < LAND_AT) return 1 - io(p / LAND_AT)
        return LAND_HOP * sin(PI * (p - LAND_AT) / (1 - LAND_AT))
    }

    fun bezier(x: Double, x1: Double = 0.77, y1: Double = 0.0, x2: Double = 0.175, y2: Double = 1.0): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        fun curve(t: Double, a: Double, b: Double) = 3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * b + t * t * t
        var lo = 0.0
        var hi = 1.0
        repeat(24) {
            val mid = (lo + hi) / 2
            if (curve(mid, x1, x2) < x) lo = mid else hi = mid
        }
        return curve((lo + hi) / 2, y1, y2)
    }

    data class Frame(
        var lift: Double = 0.0,
        var cart: Double = 0.0,
        var mode: Int = 0,
        var finger: Double = 0.0,
        var fingerAt: Int = 0,
        var fingerU: Double = 0.0,
        var press: Double = 0.0,
        var fingerX: Double = 0.0,
        var fingerY: Double = 0.0,
        var fx: Double = -999.0,
        var fy: Double = -999.0,
        var dx: Double = 0.0,
        var dy: Double = 0.0,
        var fingerDown: Boolean = false,
        var time: Double = 0.0,
        var dt: Double = 0.0,
    )

    fun at(t: Double): Frame {
        var lift = 0.55 * io(seg(t, 0.6, 1.55))
        if (t >= 1.55) lift = 0.55 + 0.45 * inside(seg(t, 1.55, 2.05))
        lift *= setDown(seg(t, 8.1, 8.9))
        val cart = io(seg(t, 6.2, 6.9)) * (1 - out(seg(t, 7.4, 8.0)))
        val mode = if (t < 0.85 || t >= REFILL) { if (t >= REFILL) 2 else 0 } else 1
        val frame = Frame(lift = lift, cart = cart, mode = mode)
        val knob = out(seg(t, 0.35, 0.5)) * (1 - out(seg(t, 1.55, 1.71)))
        if (knob > frame.finger) {
            frame.finger = knob
            frame.fingerU = io(seg(t, 0.6, 1.55))
            frame.press = knob
        }
        val stir = out(seg(t, 2.12, 2.28)) * (1 - out(seg(t, 3.3, 3.46)))
        if (stir > frame.finger) {
            frame.finger = stir
            frame.fingerAt = 1
            frame.fingerU = seg(t, 2.3, 3.3)
            frame.press = stir
        }
        val cta = out(seg(t, 6.0, 6.16)) * (1 - out(seg(t, 6.34, 6.5)))
        if (cta > frame.finger) {
            frame.finger = cta
            frame.fingerAt = 2
            frame.press = out(seg(t, 6.16, 6.25)) * (1 - out(seg(t, 6.3, 6.42)))
        }
        return frame
    }
}
