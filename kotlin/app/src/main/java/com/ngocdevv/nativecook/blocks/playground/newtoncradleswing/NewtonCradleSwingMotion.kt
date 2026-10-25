package com.ngocdevv.nativecook.blocks.playground.newtoncradleswing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 The cradle's motion, as one "folded" pendulum (same maths as newtonCradleMotion.ts).

 An ideal Newton's cradle behaves like a single pendulum angle ψ folded at the vertical: while
 ψ < 0 the left group of n balls hangs at ψ, while ψ > 0 the right group of n balls hangs at ψ.
 Each zero crossing is a collision.

 autoPlay (LOOP = 8 s), every frame a pure function of t:
   0.0 – 1.2   rest; a fingertip fades in on the left ball from 1.0.
   1.2 – 2.0   the finger pulls ball 1 out to −15° (ease-in-out).
   2.0 – 6.0   released: ψ = −A(s)·cos(ωs), period 1.02 s, amplitude × 0.97 per collision.
   6.0 – 7.4   the amplitude eases to 0, the swing dies down.
   7.4 – 8.0   rest.

 Touch: the same folded pendulum, integrated (ψ'' = −ω²·sin ψ, a little air drag, velocity
 × 0.97 at every crossing), from the angle and angular velocity the finger leaves it with.
 */

const val LOOP = 8.0
internal const val CRADLE_PERIOD = 1.02
internal const val CRADLE_OMEGA = 2 * PI / CRADLE_PERIOD
internal const val CRADLE_PULL = 15 * PI / 180
private const val PULL_LIMIT = 16 * PI / 180
private const val PULL_SOFT = 2.5 * PI / 180
private const val KEEP = 0.97
internal const val CRADLE_BALLS = 5

private const val PULL_START = 1.2
private const val RELEASE = 2.0
private const val FADE_START = 6.0
private const val FADE_END = 7.4

internal fun cradleSeg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)

internal fun cradleEaseInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

private fun easeOut(x: Double) = 1 - (1 - x).pow(3)

/** The loop's folded angle ψ at loop time t (radians, n = 1). */
internal fun cradleLoopPsi(t: Double): Double {
    if (t < PULL_START) return 0.0
    if (t < RELEASE) return -CRADLE_PULL * cradleEaseInOut(cradleSeg(t, PULL_START, RELEASE))
    val s = t - RELEASE
    val amp = CRADLE_PULL * KEEP.pow(2 * s / CRADLE_PERIOD) * (1 - cradleEaseInOut(cradleSeg(t, FADE_START, FADE_END)))
    return -amp * cos(CRADLE_OMEGA * s)
}

/** Collisions so far in the loop (shown while the balls swing). */
internal fun cradleLoopHits(t: Double): Int {
    if (t < RELEASE + CRADLE_PERIOD / 4 || t > FADE_END - 0.35) return 0
    return floor((t - RELEASE - CRADLE_PERIOD / 4) / (CRADLE_PERIOD / 2)).toInt() + 1
}

/** 0 → 1 while the scripted fingertip is on the left ball. */
internal fun cradleLoopFinger(t: Double) = easeOut(cradleSeg(t, 1.0, 1.2)) * (1 - easeOut(cradleSeg(t, RELEASE, RELEASE + 0.22)))

/** How full the energy meter is (1 = the pull, 0 = still), for the loop. */
internal fun cradleLoopEnergy(t: Double): Double {
    if (t < RELEASE) return cradleEaseInOut(cradleSeg(t, PULL_START, RELEASE))
    val s = t - RELEASE
    return KEEP.pow(2 * s / CRADLE_PERIOD) * (1 - cradleEaseInOut(cradleSeg(t, FADE_START, FADE_END)))
}

/** Each ball's angle (radians) for a folded angle ψ and a group of n balls. */
internal fun cradleBallAngles(psi: Double, n: Int, out: DoubleArray) {
    out.fill(0.0)
    if (psi < 0) for (i in 0 until n) out[i] = psi
    else if (psi > 0) for (i in CRADLE_BALLS - n until CRADLE_BALLS) out[i] = psi
}

/** Past PULL_LIMIT the pull gets stiffer: limit + soft·(1 − e^(−over/soft)). */
internal fun cradleRubberBand(theta: Double): Double {
    val a = abs(theta)
    if (a <= PULL_LIMIT) return theta
    val over = a - PULL_LIMIT
    return sign(theta) * (PULL_LIMIT + PULL_SOFT * (1 - exp(-over / PULL_SOFT)))
}

/** The touch simulation: ψ, ψ', group size and collisions. */
internal class CradleSim {
    var psi = 0.0
    var vel = 0.0
    var n = 1
    var hits = 0

    /** Advances by dt; true when a collision happened. */
    fun step(dt: Double): Boolean {
        var hit = false
        val sub = max(1, ceil(dt / (1.0 / 240)).toInt())
        val h = dt / sub
        repeat(sub) {
            val before = psi
            vel += -CRADLE_OMEGA * CRADLE_OMEGA * sin(psi) * h
            vel *= exp(-0.06 * h)
            psi += vel * h
            if ((before < 0 && psi >= 0) || (before > 0 && psi <= 0)) {
                vel *= KEEP
                hits += 1
                hit = true
            }
        }
        // Too small to see: stop.
        if (amplitude() < 0.004) {
            psi = 0.0
            vel = 0.0
        }
        return hit
    }

    fun amplitude() = sqrt(psi * psi + (vel / CRADLE_OMEGA) * (vel / CRADLE_OMEGA))
}
