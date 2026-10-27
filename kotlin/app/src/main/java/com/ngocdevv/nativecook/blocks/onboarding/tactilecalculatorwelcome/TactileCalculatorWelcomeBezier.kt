package com.ngocdevv.nativecook.blocks.onboarding.tactilecalculatorwelcome

import androidx.compose.animation.core.Easing
import kotlin.math.abs

/**
 * A cubic Bezier easing solved in double precision, the same curve as Reanimated's `Easing.bezier`
 * and CSS `cubic-bezier(x1, y1, x2, y2)`. Compose's own `CubicBezierEasing` stops at an error of
 * 0.001 in x, which is visible on the steep start of the strong ease-out used here.
 */
internal class TactileCalculatorWelcomeBezier(
    private val x1: Double,
    private val y1: Double,
    private val x2: Double,
    private val y2: Double,
) : Easing {
    override fun transform(fraction: Float): Float = at(fraction.toDouble()).toFloat()

    /** The curve's y at progress [x], clamped to 0 and 1 outside the unit interval. */
    private fun at(x: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        return coordinate(y1, y2, solve(x))
    }

    private fun coordinate(p1: Double, p2: Double, t: Double): Double {
        val a = 1.0 - 3.0 * p2 + 3.0 * p1
        val b = 3.0 * p2 - 6.0 * p1
        val c = 3.0 * p1
        return ((a * t + b) * t + c) * t
    }

    private fun slope(p1: Double, p2: Double, t: Double): Double {
        val a = 1.0 - 3.0 * p2 + 3.0 * p1
        val b = 3.0 * p2 - 6.0 * p1
        val c = 3.0 * p1
        return 3.0 * a * t * t + 2.0 * b * t + c
    }

    /** The curve parameter whose x is [x]: a few Newton steps, then bisection if the slope is flat. */
    private fun solve(x: Double): Double {
        var t = x
        for (step in 0 until 8) {
            val error = coordinate(x1, x2, t) - x
            if (abs(error) < 1e-10) return t
            val derivative = slope(x1, x2, t)
            if (abs(derivative) < 1e-6) break
            t -= error / derivative
        }
        var low = 0.0
        var high = 1.0
        t = x
        for (step in 0 until 60) {
            val value = coordinate(x1, x2, t)
            if (abs(value - x) < 1e-12) return t
            if (value < x) low = t else high = t
            t = (low + high) / 2.0
        }
        return t
    }
}

/**
 * `Easing.bezier(0.23, 1, 0.32, 1)`: the strong ease-out the React Native file uses for a key's press
 * (`EASE`), for the button's dip (`PressScale`) and for the headline's swap (`SETTLE`).
 */
internal val TactileCalculatorWelcomeEase = TactileCalculatorWelcomeBezier(0.23, 1.0, 0.32, 1.0)
