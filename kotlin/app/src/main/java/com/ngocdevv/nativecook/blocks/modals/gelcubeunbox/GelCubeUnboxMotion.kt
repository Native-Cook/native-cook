package com.ngocdevv.nativecook.blocks.modals.gelcubeunbox

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal class GelCubeUnboxSpring(val damping: Double, val stiffness: Double, val mass: Double)

/**
 * The motion of the RN block (`gcu-motion.ts`). All of it is authored (the source is a still). One value `p` (0 sealed .. 1 open) drives the lid,
 * the core, the light fan and the text; the autoplay timeline is a pure function of the loop time (LOOP = 7 s).
 */
internal object GelCubeUnboxMotion {
    const val LOOP = 7.0

    // phase table, seconds into the loop
    const val T_GATHER = 0.8
    const val T_LIFT = 1.1
    const val T_GASP = 1.6
    const val T_OPEN = 2.7
    const val T_HOLD_END = 5.2
    const val T_CLOSE_END = 6.3

    val SPRING_SEAL = GelCubeUnboxSpring(18.0, 380.0, 0.8)
    val SPRING_LID = GelCubeUnboxSpring(14.0, 120.0, 1.4)
    val SPRING_FLICK = GelCubeUnboxSpring(10.0, 120.0, 1.4)
    val SPRING_CLOSE = GelCubeUnboxSpring(16.0, 150.0, 1.4)
    val SPRING_CORE = GelCubeUnboxSpring(30.5, 322.3, 1.0)

    const val DRAG = 150.0
    const val SEAL = 6.0
    const val RESISTANCE = 0.35
    const val PROJECTION = 0.15
    const val THRESHOLD = 0.45
    const val FLICK = 900.0
    const val ANGLE = 58.0
    const val OVERSHOOT = 66.0 / 58.0
    const val RUBBER = 120.0
    const val RESUME_MS = 2000.0
    const val RESET = 0.6 // s
    const val CAUSTIC_LAG = 0.05
    const val FAN_LEAD = 0.04
    const val FLASH = 2.0 / 60.0 // s
    const val FLASH_GAIN = 0.2
    const val CALLBACK_MS = 100.0
    const val FADE = 0.2 // s
    const val PRESS_CORE = 1.25
    const val CONTACT = 0.004
    const val HAIRLINE = 0.03
    const val CUE_INTERVAL_MS = 60.0
    const val LIGHT_HEADLINE = 0.1
    const val LIGHT_CHIP = 0.52
    const val LIGHT_SHEET_TITLE = 0.62
    const val LIGHT_SHEET_BODY = 0.73

    fun clamp01(x: Double) = max(0.0, min(1.0, x))

    fun smooth(a: Double, b: Double, x: Double): Double {
        val u = clamp01((x - a) / max(0.00001, b - a))
        return u * u * (3 - 2 * u)
    }

    /** 0 -> 1 step response of a spring (response s, damping ratio zeta), evaluated analytically */
    fun springAt(t: Double, response: Double, zeta: Double): Double {
        if (t <= 0) return 0.0
        val w = (2 * PI) / response
        if (zeta >= 1) return 1 - exp(-w * t) * (1 + w * t)
        val wd = w * sqrt(max(0.0, 1 - zeta * zeta))
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + ((zeta * w) / wd) * sin(wd * t))
    }

    /** the drag's rubber band past both ends */
    fun rubber(x: Double): Double {
        if (x < 0) return -((-x * DRAG) / (1 + (-x * DRAG) / RUBBER)) / DRAG
        if (x > 1) return 1 + ((x - 1) * DRAG) / (1 + ((x - 1) * DRAG) / RUBBER) / DRAG
        return x
    }

    private fun wrap(time: Double) = ((time % LOOP) + LOOP) % LOOP

    /** A deliberate 1.6 s lift; p = .14 at 1.6 s; the last 22 % carries a soft gel spring tail. */
    fun progressAt(time: Double): Double {
        val t = wrap(time)
        if (t < T_LIFT) return 0.016 * sin(PI * smooth(T_GATHER, 1.05, t)) * (1 - smooth(1.02, T_LIFT, t))
        if (t < T_GASP) return 0.14 * smooth(T_LIFT, T_GASP, t)
        if (t < 2.35) return 0.14 + 0.64 * smooth(T_GASP, 2.35, t)
        if (t < T_OPEN) return 0.78 + 0.22 * springAt((t - 2.35) * 2, 0.65, 0.78)
        if (t < T_HOLD_END) return 1.0
        if (t < T_CLOSE_END) return 1 - smooth(T_HOLD_END, T_CLOSE_END, t)
        return 0.0
    }

    fun coreAt(time: Double): Double {
        val t = wrap(time)
        return 1 + 0.03 * (1 - cos((4 * PI * t) / LOOP)) + 0.19 * smooth(T_GATHER, 1.05, t) * (1 - smooth(1.1, 1.65, t))
    }

    fun fanAt(time: Double): Double {
        val t = wrap(time)
        return progressAt(if (t >= T_HOLD_END) min(T_CLOSE_END, t + FAN_LEAD) else t)
    }

    fun flashAt(time: Double): Double {
        val t = wrap(time)
        return if (t >= T_CLOSE_END - FLASH && t < T_CLOSE_END) FLASH_GAIN else 0.0
    }

    fun contentAt(p: Double) = smooth(0.8, 0.98, p)

    fun lightAt(p: Double, threshold: Double) = smooth(threshold - 0.04, threshold + 0.04, p)

    fun angleAt(p: Double): Double = (max(-0.02, min(OVERSHOOT, p)) * ANGLE * PI) / 180
}
