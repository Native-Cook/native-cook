package com.ngocdevv.nativecook.blocks.charts.thermalglasstiles

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal class ThermalGlassTilesSpring(val damping: Double, val stiffness: Double, val mass: Double)

/** Every motion value of the block (`tgt-motion.ts`): the loop's progress curve, the stride, the coast and the one-shot responses. */
internal object ThermalGlassTilesMotion {
    const val LOOP = 8.0
    const val PEAK_START = 2.0225737763349363

    const val RETURN_AFTER_MS = 3000.0
    val RETURN = ThermalGlassTilesSpring(24.0, 70.0, 1.2)
    val SHORTCUT = ThermalGlassTilesSpring(20.0, 220.0, 1.0)
    val SNAP = ThermalGlassTilesSpring(30.5, 322.3, 1.0)
    val RING = ThermalGlassTilesSpring(18.0, 380.0, 0.8)
    const val PEAK_STIFFNESS = 320.0
    const val PEAK_DAMPING = 14.0
    const val PEAK_MASS = 1.0
    const val DECAY_PER_MS = 0.992
    const val RUBBER = 0.25
    const val RUBBER_PT = 10.0
    const val ROUTE_SAMPLES = 256
    const val STRIDE_PT = 12.0
    const val DRAG_PT = 300.0
    const val HAPTIC_GAP_MS = 100L

    fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0) = min(hi, max(lo, v))
    fun smooth(a: Double, b: Double, v: Double): Double {
        val x = clamp((v - a) / (b - a))
        return x * x * (3 - 2 * x)
    }
    fun mix(a: Double, b: Double, v: Double) = a + (b - a) * v

    fun springAt(t: Double, response: Double, zeta: Double): Double {
        if (t <= 0) return 0.0
        val w = 2 * PI / response
        if (zeta >= 1) return 1 - exp(-w * t) * (1 + w * t)
        val wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + ((zeta * w) / wd) * sin(wd * t))
    }

    fun progressAt(time: Double): Double {
        val t = ((time % LOOP) + LOOP) % LOOP
        if (t < 0.4 || t >= 6.8) return 1.0
        if (t < 0.8) return mix(1.0, 0.05, smooth(0.4, 0.8, t))
        if (t < 2.3) return mix(0.05, 0.62, smooth(0.8, 2.3, t))
        if (t < 3.1) return 0.62
        if (t < 4) return mix(0.62, 0.98, smooth(3.1, 4.0, t))
        if (t < 4.6) return mix(0.98, 0.75, smooth(4.0, 4.6, t))
        if (t < 5) return mix(0.75, 0.8, 1 - (1 - clamp((t - 4.6) / 0.4)).pow(3))
        if (t < 5.6) return 0.8
        return mix(0.8, 1.0, smooth(5.6, 6.8, t))
    }

    fun strideAt(p: Double, time: Double, reduced: Boolean = false): Double {
        if (reduced) return 0.72
        val slow = 0.25 * sin((2 * PI * time) / LOOP) * smooth(0.0, 0.2, time) * (1 - smooth(6.6, 6.8, time))
        return 0.72 + 2 * PI * (((p - 1) * DRAG_PT) / STRIDE_PT + slow)
    }

    fun rubberProgress(v: Double): Double {
        val edge = clamp(v)
        val over = v - edge
        return edge + (over * RUBBER) / (1 + (abs(over) * DRAG_PT) / RUBBER_PT)
    }

    fun peakAt(t: Double) = smooth(PEAK_START, PEAK_START + 0.08, t) * (1 - smooth(PEAK_START + 0.62, PEAK_START + 0.7, t))

    /** Field clock returns around the noise circle before the still tail; the final 1.2 s is the exact poster. */
    fun sceneTimeAt(time: Double): Double {
        val t = ((time % LOOP) + LOOP) % LOOP
        if (t < 0.4 || t >= 6.8) return 0.0
        if (t < 5.6) return t - 0.4
        return mix(5.2, 8.0, smooth(5.6, 6.8, t))
    }

    /** Analytic response of the numeral spring {damping 14, stiffness 320, mass 1}. */
    fun bpmPulseAt(age: Double): Double {
        if (age <= 0 || age >= 0.7) return 1.0
        val w = sqrt(PEAK_STIFFNESS / PEAK_MASS)
        val zeta = PEAK_DAMPING / (2 * sqrt(PEAK_STIFFNESS * PEAK_MASS))
        return 1 + 0.06 * clamp(springAt(age, (2 * PI) / w, zeta)) * (1 - smooth(0.24, 0.7, age))
    }

    /** Exact 0.992-per-ms coast; independent of frame rate. Returns position and velocity. */
    fun coastAt(origin: Double, velocity: Double, age: Double, out: DoubleArray) {
        val lambda = -ln(DECAY_PER_MS) * 1000
        val attenuation = exp(-lambda * max(0.0, age))
        out[0] = origin + (velocity * (1 - attenuation)) / lambda
        out[1] = velocity * attenuation
    }
}

