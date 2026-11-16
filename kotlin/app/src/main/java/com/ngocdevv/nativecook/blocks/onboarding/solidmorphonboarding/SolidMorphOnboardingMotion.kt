package com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal class SolidMorphOnboardingSpring(val damping: Double, val stiffness: Double, val mass: Double)

/** The loop and the gesture constants of the RN block (`smo-motion.ts`). The hover is observed; the lathe and the page changes are ours. */
internal object SolidMorphOnboardingMotion {
    const val LOOP = 9.0
    const val T_START = 0.6
    const val T_HALF = 1.0
    const val T_HOLD_END = 1.8
    const val T_PAGE_TWO = 2.3
    const val T_NEXT = 2.8
    const val T_PAGE_THREE = 5.3
    const val T_RETURN = 6.0
    const val T_HOME = 8.55

    const val DRAG = 320.0
    const val PROJECTION = 0.12
    const val RESUME_AFTER = 2.0
    const val RESET_DURATION = 0.6
    const val SPIN = 1.5
    const val TILT_LIMIT = 12.0
    const val END_TILT = 8.0
    const val END_SPIN = 20.0
    const val PRESS_SLOP = 10.0
    const val EDGE_GUARD = 20.0
    const val VELOCITY_FADE = 0.08 // s
    const val TOUCH_RESUME = 2600.0 // ms
    const val PRECESSION_VELOCITY = 0.02
    const val RETURN_FADE = 0.22 // s

    val LATHE = SolidMorphOnboardingSpring(20.0, 140.0, 1.0)
    val CUSHION = SolidMorphOnboardingSpring(8.0, 170.0, 1.0)
    val BASE = SolidMorphOnboardingSpring(14.0, 200.0, 1.0)
    val GLIDE = SolidMorphOnboardingSpring(22.1, 157.9, 1.0)
    val PRECESSION = SolidMorphOnboardingSpring(2 / 1.2, 1 / (1.2 * 1.2), 1.0)

    fun clamp(x: Double, a: Double = 0.0, b: Double = 1.0) = max(a, min(b, x))

    fun smooth(a: Double, b: Double, x: Double): Double {
        val u = clamp((x - a) / max(0.00001, b - a))
        return u * u * (3 - 2 * u)
    }

    fun ease(x: Double): Double {
        val u = clamp(x)
        return u * u * u * (u * (u * 6 - 15) + 10)
    }

    /** the loop's page progress 0 .. 2 at loop time t */
    fun progressAt(t: Double): Double {
        val x = ((t % LOOP) + LOOP) % LOOP
        if (x < T_START) return 0.0
        if (x < T_HALF) return 0.5 * ease((x - T_START) / (T_HALF - T_START))
        if (x < T_HOLD_END) return 0.5
        if (x < T_PAGE_TWO) return 0.5 + 0.5 * ease((x - T_HOLD_END) / (T_PAGE_TWO - T_HOLD_END))
        if (x < T_NEXT) return 1.0
        if (x < T_PAGE_THREE) return 1 + ease((x - T_NEXT) / (T_PAGE_THREE - T_NEXT))
        if (x < T_RETURN) return 2.0
        if (x < T_HOME) return 2 * (1 - ease((x - T_RETURN) / (T_HOME - T_RETURN)))
        return 0.0
    }

    fun rateAt(t: Double) = (progressAt(t + 0.001) - progressAt(t - 0.001)) / 0.002

    private fun damped(t: Double, damping: Double, stiffness: Double): Double {
        if (t <= 0) return 0.0
        return exp((-damping * t) / 2) * sin(sqrt(max(0.01, stiffness - (damping * damping) / 4)) * t)
    }

    fun bobAt(t: Double) = 4 * (damped(t - T_PAGE_TWO, 10.0, 180.0) + damped(t - T_PAGE_THREE, 10.0, 180.0)) * (1 - smooth(T_RETURN, T_HOME, t))

    fun baseAt(t: Double) = 1.5 * (damped(t - T_PAGE_TWO - 0.3, 14.0, 200.0) + damped(t - T_PAGE_THREE - 0.3, 14.0, 200.0)) * (1 - smooth(T_RETURN, T_HOME, t))

    fun hoverAt(t: Double) = sin(((((t % LOOP) + LOOP) % LOOP) / LOOP) * PI * 4)

    fun rubber(x: Double, end: Double): Double {
        if (x < 0) return (x * 0.25) / (1 + abs(x))
        if (x > end) return end + ((x - end) * 0.25) / (1 + x - end)
        return x
    }

    class Mix(val from: Int, val to: Int, val t: Double)

    fun pageMix(progress: Double, count: Int): Mix {
        val p = clamp(progress, 0.0, max(0, count - 1).toDouble())
        val from = min(floor(p).toInt(), max(0, count - 2))
        return Mix(from, min(from + 1, count - 1), p - from)
    }

    /** the lathe's rate is the derivative of its eased angle, not the raw page velocity */
    fun spinRate(progress: Double, pageRate: Double, turns: Double = SPIN): Double {
        val u = clamp(progress - floor(progress))
        return pageRate * turns * 30 * u * u * (1 - u) * (1 - u)
    }

    fun pressAt(distance: Double): Double {
        val down = max(0.0, distance)
        // 0.5 pt/pt at first, stiffening to 0.2; the dish is a real 4 pt stop.
        return clamp((if (down <= 20) down * 0.5 else 10 + (down - 20) * 0.2) / 18)
    }

    private fun pulseAt(t: Double, at: Double, duration: Double) = if (t < at) 0.0 else 1 - smooth(0.0, duration, t - at)

    fun arrivalAt(t: Double) = max(pulseAt(t, T_PAGE_TWO, 0.1), pulseAt(t, T_PAGE_THREE, 0.1))

    fun basePulseAt(t: Double) = max(pulseAt(t, T_PAGE_TWO + 0.3, 0.2), pulseAt(t, T_PAGE_THREE + 0.3, 0.2))
}
