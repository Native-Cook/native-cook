package com.ngocdevv.nativecook.blocks.navigation.ropetabbar

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 The rope and its knot, as closed-form functions of time (same maths as ropeTabBarMotion.ts).

 The rope is pinned at both screen edges along the top of the tab bar. Its height at x (u = x / w):
   slack sag SAG · sin(πu)
   + the knot's weight: a tent pointed at the knot, KNOT_WEIGHT · 4·uk·(1 − uk) deep
   + whip: four string modes sin(nπu) rung by kicks (a time, a place, a size), faded to exactly 0
     between 1.4 and 1.9 s after the kick, so the loop closes on itself.

 autoPlay (LOOP = 8 s): rest on Explore, to Gear at 1.3, to Routes at 3.6, back to Explore at 5.6
 with a harder whip, rest.
 */

const val LOOP = 8.0
internal const val ROPE_TABS = 4
internal const val ROPE_SAG = 4.0
private const val KNOT_WEIGHT = 7.0
private const val WHIP_HZ = 1.8
private const val WHIP_DECAY = 0.5
internal const val ROPE_KNOT_W = 7.5
internal const val ROPE_KNOT_Z = 0.78
private const val LAND_AFTER = 0.42

/** [start, from, to] */
internal val RopeLoopMoves: Array<DoubleArray> = arrayOf(
    doubleArrayOf(1.3, 0.0, 2.0),
    doubleArrayOf(3.6, 2.0, 1.0),
    doubleArrayOf(5.6, 1.0, 0.0),
)

internal fun ropeTabU(i: Double) = (i + 0.5) / ROPE_TABS

/** Kicks: [time, u, size (dp, + = down)], flattened. */
internal val RopeLoopKicks: DoubleArray = run {
    val out = ArrayList<Double>()
    RopeLoopMoves.forEachIndexed { i, m ->
        val d = abs(m[2] - m[1]).pow(0.6)
        val hard = if (i == RopeLoopMoves.size - 1) 1.45 else 1.0
        out += listOf(m[0] + 0.05, ropeTabU(m[1]), -3.2 * d * hard)
        out += listOf(m[0] + LAND_AFTER, ropeTabU(m[2]), 6 * d * hard)
    }
    out.toDoubleArray()
}

internal fun ropeSeg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)

internal fun ropeEaseInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

/** Step response of a spring from rest: position 0 → 1. */
internal fun ropeSpringStep(tau: Double, w: Double, z: Double): Double {
    if (tau <= 0) return 0.0
    val wd = w * sqrt(1 - z * z)
    return 1 - exp(-z * w * tau) * (cos(wd * tau) + (z * w / wd) * sin(wd * tau))
}

/** The loop's knot place p at loop time t. */
internal fun ropeLoopKnot(t: Double): Double {
    var p = 0.0
    for (m in RopeLoopMoves) p += (m[2] - m[1]) * ropeSpringStep(t - m[0], ROPE_KNOT_W, ROPE_KNOT_Z)
    return if (t > 7.9) 0.0 else p
}

/** The rope's height below its pinned line at u, for a knot at uk, from kicks at time t. */
internal fun ropeAt(u: Double, uk: Double, kicks: DoubleArray, count: Int, t: Double, gain: Double): Double {
    var y = ROPE_SAG * sin(PI * u)
    val depth = KNOT_WEIGHT * 4 * uk * (1 - uk)
    y += depth * (if (u < uk) u / max(uk, 0.001) else (1 - u) / max(1 - uk, 0.001))
    var k = 0
    while (k + 2 < count) {
        val tau = t - kicks[k]
        if (tau > 0 && tau < 1.9) {
            val env = exp(-tau / WHIP_DECAY) * (1 - ropeEaseInOut(ropeSeg(tau, 1.4, 1.9))) * kicks[k + 2] * gain
            for (n in 1..4) {
                y += (env / n) * sin(n * PI * kicks[k + 1]) * sin(n * PI * u) * sin(n * 2 * PI * WHIP_HZ * tau)
            }
        }
        k += 3
    }
    return y
}

/** A tap ripple: 0 → 1 over its life, or −1 when none is showing. */
internal fun ropeRippleAt(t: Double, start: Double): Double {
    val tau = t - start
    if (tau < -0.12 || tau > 0.55) return -1.0
    return ropeSeg(tau, -0.12, 0.55)
}

internal fun ropeClampTab(x: Double) = min(ROPE_TABS - 1.0, max(0.0, x))
