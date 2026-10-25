package com.ngocdevv.nativecook.blocks.sheets.rollerblindsheet

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
 Roller Blind Sheet — geometry and the loop's script, as pure functions of the loop time (frame at
 t = LOOP equals frame at 0), ported from rollerBlindMotion.ts.

 The blind hangs from a roller across the top of the window; its hem (a wooden bar) sits at hemY.
 Detents for the hem: UP (rolled up), HALF (the window's sill), FULL (covers the room's controls).

 autoPlay script (LOOP = 9 s):
   0.0 – 1.2   rest: the blind half down at the sill.
   1.0 – 1.2   a finger lands on the bead.
   1.2 – 3.2   pull down to FULL (ease-in-out, a 3 % lift first), 14 dp past it.
   3.2 – 3.6   detent bounce; the finger lets go, the cord swings 10°.
   3.6 – 5.6   hold.
   5.6 – 5.9   tug: the bead 20 dp down (ease-out), let go.
   5.9 – 7.0   the spring roller winds the blind up; 7.0 – 7.2 the hem knocks the roll.
   7.2 – 7.5   a finger takes the bead again.
   7.5 – 8.3   pull down to the sill, 8 dp past it; 8.3 – 8.6 settle.
   8.6 – 9.0   rest.
 */

internal const val LOOP = 9.0

internal class BlindGeo(val w: Double, val h: Double, top: Double, bottom: Double) {
    val unit = max(0.74, min(1.15, min(w / 393, h / 852)))
    val rollerY = top + 116 * unit
    val winX = 30.0
    val winY = rollerY + 16 * unit
    val winW = w - 60
    val winH = min(330 * unit, h * 0.39)
    val up = rollerY + 22 * unit
    val half = winY + winH + 8 * unit
    val full = h - bottom - 112 * unit
    val x0 = 20.0
    val x1 = w - 20
    val controlsY = winY + winH + 22 * unit
}

private fun clamp01(x: Double) = min(1.0, max(0.0, x))
private fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
private fun easeOut(x: Double): Double {
    val u = 1 - x
    return 1 - u * u * u
}
internal fun blindEaseInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

internal fun blindHemAt(g: BlindGeo, t: Double): Double {
    val over = 14 * g.unit
    if (t < 1.2) return g.half
    if (t < 3.2) {
        val u = seg(t, 1.2, 3.2)
        val span = g.full - g.half
        val lift = -0.03 * (g.full - g.up) * sin(PI * clamp01(u / 0.16))
        return g.half + (span + over) * blindEaseInOut(u) + lift
    }
    if (t < 3.6) {
        val u = seg(t, 3.2, 3.6)
        return g.full + over * cos(PI * 3 * u) * (1 - u) * (1 - u)
    }
    if (t < 5.6) return g.full
    val tug = 20 * g.unit
    if (t < 5.9) return g.full + tug * easeOut(seg(t, 5.6, 5.9))
    if (t < 7.0) return g.up + (g.full + tug - g.up) * (1 - easeOut(seg(t, 5.9, 7.0)))
    if (t < 7.2) return g.up + 4 * g.unit * sin(PI * seg(t, 7.0, 7.2))
    if (t < 7.5) return g.up
    val small = 8 * g.unit
    if (t < 8.3) return g.up + (g.half + small - g.up) * blindEaseInOut(seg(t, 7.5, 8.3))
    if (t < 8.6) {
        val u = seg(t, 8.3, 8.6)
        return g.half + small * cos(PI * 3 * u) * (1 - u) * (1 - u)
    }
    return g.half
}

private fun swingFrom(t: Double, t0: Double, a: Double, tau: Double): Double {
    if (t < t0) return 0.0
    val x = t - t0
    return a * exp(-x / tau) * sin(PI * 2 * 1.4 * x)
}

/** The cord's angle from straight down (degrees) at loop time t. */
internal fun blindCordAt(t: Double): Double {
    if (t >= 1.2 && t < 3.2) return 0.0
    if (t >= 5.6 && t < 5.9) return 0.0
    if (t >= 7.5 && t < 8.3) return 0.0
    var a = swingFrom(t, 3.2, 10.0, 0.5) * (1 - seg(t, 5.3, 5.6))
    a += swingFrom(t, 5.9, -8.0, 0.45)
    a += swingFrom(t, 7.0, 12.0, 0.35) * (1 - blindEaseInOut(seg(t, 7.2, 7.5)))
    a += swingFrom(t, 8.3, 6.0, 0.3) * (1 - blindEaseInOut(seg(t, 8.6, 9.0)))
    return a
}

/** The finger on the bead at loop time t (alpha). */
internal fun blindFingerAt(t: Double): Double = when {
    t >= 1.0 && t < 3.4 -> seg(t, 1.0, 1.2) * (1 - seg(t, 3.2, 3.4))
    t >= 5.4 && t < 6.1 -> seg(t, 5.4, 5.6) * (1 - seg(t, 5.9, 6.1))
    t >= 7.2 && t < 8.5 -> seg(t, 7.2, 7.4) * (1 - seg(t, 8.3, 8.5))
    else -> 0.0
}

internal fun blindCoverOf(g: BlindGeo, hem: Double) = clamp01((hem - g.winY) / g.winH)

internal fun blindRollRadius(g: BlindGeo, hem: Double): Double {
    val core = 7.5 * g.unit
    val thick = 13.5 * g.unit
    val left = clamp01(1 - (hem - g.up) / (g.full + 40 * g.unit - g.up))
    return sqrt(core * core + (thick * thick - core * core) * left)
}

internal fun blindRubberHem(g: BlindGeo, y: Double): Double {
    if (y > g.full) {
        val o = y - g.full
        return g.full + o / (1 + o / (60 * g.unit))
    }
    if (y < g.up) {
        val o = g.up - y
        return g.up - o / (1 + o / (16 * g.unit))
    }
    return y
}

internal fun blindDetentFor(g: BlindGeo, hem: Double, v: Double): Double {
    val aim = hem + v * 0.2
    var best = g.up
    for (s in doubleArrayOf(g.half, g.full)) if (abs(s - aim) < abs(best - aim)) best = s
    return best
}
