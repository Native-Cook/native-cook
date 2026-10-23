package com.ngocdevv.nativecook.blocks.media.morphingprintlayout

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 The layouts of Morphing Print Layout and how one becomes the next (a port of
 MorphingPrintLayoutModel.swift; the numbers are the same).

 Five film formats, in design dp (scaled by the screen width / 393, at most 430 / 393). A frame is a
 white card; its wells are square photo windows placed from the frame's top-left corner:
   0 Single   270 × 322   one 242 well at (14, 14)
   1 Double   322 × 196   two 141 wells, 12 apart
   2 Triple   322 × 150   three 94 wells, 6 apart
   3 Four     290 × 316   2 × 2 wells of 125, 12 apart
   4 Six      322 × 242   3 × 2 wells of 94, 6 apart
 Every layout lists six wells: those it does not use sit on its last real well, under it, so a growing
 layout splits a photo out from under its neighbour and a shrinking one tucks it back.

 The layout at a fractional page p is the linear blend of its two neighbours (extrapolated past the
 ends). Between Triple and Four the prints come loose: with λ = sin(π (p − 2)), each of the four wells
 grows its own white border (8 dp, 22 dp at the bottom), tilts and spreads out, and the big frame fades
 to 1 − 1.6 λ (gone at λ = 0.625).

 autoPlay timeline (LOOP = 7 s):
   0.0–0.8   rest on Single.
   0.8       → Double, spring (1 − e^(−9u)(cos 12u + 0.75 sin 12u), ~9 % overshoot, settled by 0.6 s).
   1.9       → Triple, same spring.
   3.0–4.1   → Four, ease in-out over 1.1 s, the prints scattering and gathering on the way.
   4.7       → Six, spring.
   5.8       back to Single in one sweep, critically damped (1 − (1 + 9u) e^(−9u)).
   6.7–7.0   rest on Single, the same frame as t = 0.
 */

internal class MplWell(val x: Double, val y: Double, val s: Double)

internal class MplFormat(val name: String, val caption: String, val w: Double, val h: Double, val wells: List<MplWell>, val count: Int)

internal class MplShape(val w: Double, val h: Double, val wells: List<MplWell>, val shown: Int, val loose: Double)

internal object MorphingPrintLayoutModel {
    const val LOOP = 7.0

    private fun make(name: String, caption: String, w: Double, h: Double, real: List<DoubleArray>): MplFormat {
        val wells = real.map { MplWell(it[0], it[1], it[2]) }.toMutableList()
        while (wells.size < 6) wells.add(wells[real.size - 1])
        return MplFormat(name, caption, w, h, wells, real.size)
    }

    val formats = listOf(
        make("Single", "One photo, a classic square", 270.0, 322.0, listOf(doubleArrayOf(14.0, 14.0, 242.0))),
        make("Double", "Two photos side by side", 322.0, 196.0, listOf(doubleArrayOf(14.0, 14.0, 141.0), doubleArrayOf(167.0, 14.0, 141.0))),
        make("Triple", "Three photos in a strip", 322.0, 150.0,
            listOf(doubleArrayOf(14.0, 14.0, 94.0), doubleArrayOf(114.0, 14.0, 94.0), doubleArrayOf(214.0, 14.0, 94.0))),
        make("Four", "Four photos in a grid", 290.0, 316.0,
            listOf(doubleArrayOf(14.0, 14.0, 125.0), doubleArrayOf(151.0, 14.0, 125.0), doubleArrayOf(14.0, 151.0, 125.0), doubleArrayOf(151.0, 151.0, 125.0))),
        make("Six", "Six photos, a contact sheet", 322.0, 242.0,
            listOf(
                doubleArrayOf(14.0, 14.0, 94.0), doubleArrayOf(114.0, 14.0, 94.0), doubleArrayOf(214.0, 14.0, 94.0),
                doubleArrayOf(14.0, 114.0, 94.0), doubleArrayOf(114.0, 114.0, 94.0), doubleArrayOf(214.0, 114.0, 94.0),
            )),
    )

    /** Where the loose prints go between Triple and Four: offset (dp) and tilt (degrees) at λ = 1. */
    val scatter = listOf(doubleArrayOf(-24.0, -20.0, -10.0), doubleArrayOf(22.0, -14.0, 8.0), doubleArrayOf(-18.0, 18.0, 7.0), doubleArrayOf(26.0, 22.0, -9.0))

    fun shape(p: Double, reduce: Boolean = false): MplShape {
        val n = formats.size
        val a = min(n - 2, max(0, floor(p).toInt()))
        val f = p - a
        val A = formats[a]
        val B = formats[a + 1]
        fun mix(u: Double, v: Double) = u + (v - u) * f
        val wells = (0 until 6).map { i -> MplWell(mix(A.wells[i].x, B.wells[i].x), mix(A.wells[i].y, B.wells[i].y), mix(A.wells[i].s, B.wells[i].s)) }
        val loose = if (!reduce && p > 2 && p < 3) sin(PI * (p - 2)) else 0.0
        val shown = if (f <= 0.001) A.count else if (f >= 0.999) B.count else max(A.count, B.count)
        return MplShape(mix(A.w, B.w), mix(A.h, B.h), wells, shown, loose)
    }

    fun spring(u: Double): Double = if (u <= 0) 0.0 else 1 - exp(-9 * u) * (cos(12 * u) + 0.75 * sin(12 * u))
    fun critical(u: Double): Double = if (u <= 0) 0.0 else 1 - (1 + 9 * u) * exp(-9 * u)
    fun inOut(x: Double): Double {
        val t = min(1.0, max(0.0, x))
        return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
    }

    /** The page at loop time t. */
    fun page(t: Double): Double = when {
        t < 0.8 -> 0.0
        t < 1.9 -> spring(t - 0.8)
        t < 3.0 -> 1 + spring(t - 1.9)
        t < 4.7 -> 2 + inOut((t - 3.0) / 1.1)
        t < 5.8 -> 3 + spring(t - 4.7)
        else -> 4 - 4 * critical(t - 5.8)
    }
}
