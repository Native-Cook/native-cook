package com.ngocdevv.nativecook.blocks.media.fisheyememorywall

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 The wall, its memories, the lens and the timeline of Fisheye Memory Wall (a port of
 FisheyeMemoryWallModel.swift). Every frame is a pure function of time (autoPlay) or of the touch state.
*/

private fun d(vararg v: Number) = DoubleArray(v.size) { v[it].toDouble() }

internal class FmwTile(
    val col: Int, val row: Int, val w: Int, val h: Int,
    val design: Int, val tint: Int, val flip: Boolean, val month: Int, val day: Int,
) {
    val x = col * FmwModel.CELL + FmwModel.GUTTER / 2
    val y = row * FmwModel.CELL + FmwModel.GUTTER / 2
    val width = w * FmwModel.CELL - FmwModel.GUTTER
    val height = h * FmwModel.CELL - FmwModel.GUTTER
    val radius = 5.0 + 2.0 * min(w, h)
}

/** Pan offset from rest (wall points), bulge strength and the focus ring's opacity. */
internal data class FmwPose(val x: Double = 0.0, val y: Double = 0.0, val k: Double = 1.0, val ring: Double = 1.0)

internal object FmwModel {
    // Loop

    /** One period of the autoPlay loop, in seconds. */
    const val LOOP = 9.0

    // Wall

    /** One cell of the wall grid, the gutter between tiles, and the repeating period (7 × 10 cells). */
    const val CELL = 44.0
    const val GUTTER = 5.0
    const val PERIOD_W = CELL * 7
    const val PERIOD_H = CELL * 10
    /** Atlas pixels per point. */
    const val ATLAS_SCALE = 4.0

    /** The lens centre rests in the middle of the cake tile (index 11). */
    const val REST_X = 132.0
    const val REST_Y = 132.0

    /** col, row, w, h, design, tint, flip, month (0 = Jan), day — packed once from a seeded greedy fill. */
    private val LAYOUT = arrayOf(
        intArrayOf(0, 0, 1, 1, 0, 1, 1, 6, 15), intArrayOf(1, 0, 2, 1, 12, 3, 1, 6, 25), intArrayOf(3, 0, 1, 1, 11, 0, 0, 7, 12),
        intArrayOf(4, 0, 1, 1, 10, 2, 1, 2, 21), intArrayOf(5, 0, 2, 2, 6, 0, 1, 0, 17), intArrayOf(0, 1, 1, 1, 13, 2, 1, 1, 13),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 4, 18), intArrayOf(2, 1, 1, 1, 2, 0, 0, 11, 13), intArrayOf(3, 1, 2, 1, 3, 1, 0, 7, 17),
        intArrayOf(0, 2, 1, 1, 9, 1, 0, 1, 21), intArrayOf(1, 2, 1, 1, 7, 2, 0, 4, 16), intArrayOf(2, 2, 2, 2, 4, 2, 1, 4, 11),
        intArrayOf(4, 2, 1, 1, 5, 0, 0, 11, 22), intArrayOf(5, 2, 2, 2, 1, 2, 0, 11, 24), intArrayOf(0, 3, 2, 2, 0, 0, 1, 5, 21),
        intArrayOf(4, 3, 1, 1, 9, 0, 0, 9, 26), intArrayOf(2, 4, 1, 1, 3, 1, 1, 4, 16), intArrayOf(3, 4, 1, 1, 10, 0, 1, 1, 14),
        intArrayOf(4, 4, 1, 1, 11, 2, 1, 11, 26), intArrayOf(5, 4, 2, 2, 7, 0, 0, 10, 14), intArrayOf(0, 5, 2, 1, 5, 3, 1, 4, 8),
        intArrayOf(2, 5, 1, 1, 13, 3, 1, 9, 18), intArrayOf(3, 5, 2, 2, 8, 2, 1, 6, 7), intArrayOf(0, 6, 1, 1, 6, 0, 1, 0, 19),
        intArrayOf(1, 6, 1, 1, 4, 1, 1, 11, 27), intArrayOf(2, 6, 1, 2, 2, 0, 0, 7, 13), intArrayOf(5, 6, 2, 2, 12, 1, 0, 10, 5),
        intArrayOf(0, 7, 1, 1, 11, 2, 1, 6, 13), intArrayOf(1, 7, 1, 2, 5, 0, 1, 1, 18), intArrayOf(3, 7, 1, 1, 10, 1, 0, 5, 19),
        intArrayOf(4, 7, 1, 1, 6, 0, 1, 5, 19), intArrayOf(0, 8, 1, 2, 3, 1, 0, 2, 11), intArrayOf(2, 8, 2, 2, 13, 1, 0, 1, 5),
        intArrayOf(4, 8, 1, 1, 8, 3, 1, 11, 18), intArrayOf(5, 8, 2, 2, 2, 2, 1, 2, 18), intArrayOf(1, 9, 1, 1, 9, 1, 1, 11, 18),
        intArrayOf(4, 9, 1, 1, 12, 1, 0, 3, 15),
    )

    val TILES = LAYOUT.map { FmwTile(it[0], it[1], it[2], it[3], it[4], it[5], it[6] == 1, it[7], it[8]) }

    val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    /** Title and place of each design. */
    val CAPTIONS = listOf(
        "Sunday shakshuka" to "Home, Alfama",
        "Last light" to "Pelican Bay",
        "Ridge walk, 6.2 km" to "Sella Pass",
        "Flat white, window seat" to "Café Lune",
        "Mila turns seven" to "Our kitchen",
        "Night tram home" to "Old Town",
        "First tulips" to "Market Street",
        "Beach day, finally" to "Praia do Sol",
        "Golden hour" to "Rooftop",
        "Blue hour" to "Harbour walk",
        "A day worth marking" to "Journal",
        "Up at dawn" to "Valley of Mist",
        "Lemon tart, first try" to "Home",
        "Camp under the stars" to "Lake Vire",
    )

    /** Per-instance tints laid over a tile at 12 % (0 = none). */
    val TINTS = longArrayOf(0x000000, 0xFF9A5A, 0x5AA0FF, 0xFF6F91)

    // Artwork
    //
    // Each design is a list of shape ops in a unit square (x right, y down), cover-fitted to the tile:
    // 0 gradient c1 c2 c3 (whole tile) · 1 circle x y r color alpha · 2 ellipse x y rx ry color alpha ·
    // 3 rect x y w h radius color alpha · 4 polygon color alpha x1 y1 x2 y2 … ·
    // 5 line x1 y1 x2 y2 width color alpha (round caps) · 6 blob x y r color alpha (radial fade) ·
    // 7 the tile's date (month over day).

    val DESIGNS: List<List<DoubleArray>> = listOf(
        // 0 shakshuka in a pan, on a wooden table
        listOf(d(0, 0xC9B79C, 0xB9A385, 0xA48C6E), d(5, 0.84, 0.66, 1.05, 0.86, 0.09, 0x1E2228, 1),
         d(1, 0.5, 0.53, 0.41, 0x14171C, 0.45), d(1, 0.5, 0.5, 0.4, 0x2B3038, 1), d(1, 0.5, 0.5, 0.34, 0xC8442C, 1),
         d(6, 0.42, 0.4, 0.3, 0xE8683E, 0.6),
         d(1, 0.37, 0.42, 0.095, 0xFFF6E8, 1), d(1, 0.37, 0.42, 0.042, 0xF2B530, 1),
         d(1, 0.61, 0.58, 0.095, 0xFFF6E8, 1), d(1, 0.61, 0.58, 0.042, 0xF2B530, 1),
         d(1, 0.59, 0.34, 0.028, 0x3F8A3A, 1), d(1, 0.41, 0.65, 0.024, 0x3F8A3A, 1), d(1, 0.68, 0.43, 0.022, 0x4FA048, 1),
         d(1, 0.3, 0.58, 0.02, 0x4FA048, 1)),
        // 1 sunset over the sea
        listOf(d(0, 0x2B3A67, 0xE8735A, 0xF6B26B), d(6, 0.5, 0.6, 0.45, 0xFFC27A, 0.7), d(1, 0.5, 0.6, 0.15, 0xFFE3A8, 1),
         d(3, 0, 0.62, 1, 0.4, 0, 0x1F3E5C, 1), d(3, 0, 0.62, 1, 0.05, 0, 0x2C5476, 1),
         d(3, 0.24, 0.66, 0.52, 0.022, 0.011, 0xFFD08A, 0.75), d(3, 0.33, 0.72, 0.34, 0.018, 0.009, 0xFFD08A, 0.55),
         d(3, 0.42, 0.78, 0.16, 0.014, 0.007, 0xFFD08A, 0.4)),
        // 2 mountain ridge
        listOf(d(0, 0x8DB8E2, 0xC6DDF0, 0xEAF2F6), d(1, 0.78, 0.2, 0.07, 0xFFF7E0, 1),
         d(4, 0x4B5D78, 1, 0, 0.8, 0.34, 0.3, 0.55, 0.58, 0.72, 0.4, 1, 0.76, 1, 1, 0, 1),
         d(4, 0xF4F7FB, 1, 0.34, 0.3, 0.25, 0.43, 0.31, 0.41, 0.36, 0.46, 0.42, 0.4),
         d(4, 0xF4F7FB, 1, 0.72, 0.4, 0.66, 0.48, 0.71, 0.47, 0.75, 0.5, 0.79, 0.47),
         d(4, 0x2F4A3F, 1, 0, 0.86, 0.3, 0.68, 0.6, 0.84, 1, 0.7, 1, 1, 0, 1)),
        // 3 a flat white on a saucer
        listOf(d(0, 0xE9DCCB, 0xDCCAB4, 0xCDB79C), d(2, 0.52, 0.64, 0.36, 0.14, 0xA8927A, 0.45),
         d(2, 0.5, 0.6, 0.35, 0.14, 0xF7F3EE, 1), d(5, 0.74, 0.5, 0.86, 0.5, 0.07, 0xFBF8F4, 1),
         d(1, 0.5, 0.5, 0.26, 0xFBF8F4, 1), d(1, 0.5, 0.5, 0.21, 0x8A5A3B, 1), d(1, 0.5, 0.5, 0.18, 0xB07B52, 1),
         d(1, 0.455, 0.47, 0.06, 0xF3E6D6, 1), d(1, 0.545, 0.47, 0.06, 0xF3E6D6, 1),
         d(4, 0xF3E6D6, 1, 0.398, 0.49, 0.602, 0.49, 0.5, 0.6)),
        // 4 a birthday cake with one candle
        listOf(d(0, 0xF7C9D2, 0xF2B3C2, 0xE89BB0), d(6, 0.5, 0.28, 0.3, 0xFFF1C2, 0.8), d(2, 0.5, 0.84, 0.42, 0.06, 0xFFFFFF, 1),
         d(3, 0.2, 0.5, 0.6, 0.33, 0.05, 0xFFF4EC, 1), d(3, 0.2, 0.5, 0.6, 0.09, 0.05, 0xE05A7A, 1),
         d(1, 0.3, 0.585, 0.032, 0xE05A7A, 1), d(1, 0.47, 0.6, 0.038, 0xE05A7A, 1), d(1, 0.66, 0.585, 0.03, 0xE05A7A, 1),
         d(3, 0.2, 0.7, 0.6, 0.03, 0, 0xF6B8C6, 1), d(5, 0.5, 0.34, 0.5, 0.5, 0.036, 0x7FC6E8, 1),
         d(2, 0.5, 0.28, 0.032, 0.055, 0xFFC74A, 1), d(2, 0.5, 0.29, 0.014, 0.025, 0xFFF4D0, 1)),
        // 5 a skyline at night
        listOf(d(0, 0x0E1430, 0x1E2A5A, 0x3B3F7A), d(6, 0.75, 0.22, 0.2, 0xF4F1DE, 0.35), d(1, 0.75, 0.22, 0.06, 0xF4F1DE, 1),
         d(3, 0, 0.55, 0.18, 0.45, 0, 0x121733, 1), d(3, 0.16, 0.42, 0.16, 0.58, 0, 0x171C3D, 1),
         d(3, 0.3, 0.6, 0.14, 0.4, 0, 0x10142C, 1), d(3, 0.42, 0.35, 0.18, 0.65, 0, 0x1A2046, 1),
         d(3, 0.58, 0.52, 0.15, 0.48, 0, 0x121733, 1), d(3, 0.71, 0.45, 0.14, 0.55, 0, 0x161B3A, 1),
         d(3, 0.83, 0.62, 0.17, 0.38, 0, 0x10142C, 1),
         d(3, 0.2, 0.48, 0.03, 0.03, 0, 0xFFD27A, 1), d(3, 0.26, 0.56, 0.03, 0.03, 0, 0xFFD27A, 0.8),
         d(3, 0.47, 0.42, 0.03, 0.03, 0, 0xFFD27A, 1), d(3, 0.53, 0.5, 0.03, 0.03, 0, 0xFFD27A, 0.7),
         d(3, 0.47, 0.62, 0.03, 0.03, 0, 0xFFD27A, 0.9), d(3, 0.62, 0.6, 0.03, 0.03, 0, 0xFFD27A, 0.8),
         d(3, 0.75, 0.53, 0.03, 0.03, 0, 0xFFD27A, 1), d(3, 0.88, 0.7, 0.03, 0.03, 0, 0xFFD27A, 0.8),
         d(3, 0.05, 0.64, 0.03, 0.03, 0, 0xFFD27A, 0.7)),
        // 6 a tulip
        listOf(d(0, 0xF3EFD8, 0xE7E9C9, 0xD6E2B8), d(6, 0.5, 0.42, 0.3, 0xFFFFFF, 0.6), d(5, 0.5, 0.5, 0.5, 1.05, 0.04, 0x4E8B4A, 1),
         d(4, 0x5E9E55, 1, 0.5, 0.88, 0.28, 0.62, 0.36, 0.82), d(4, 0x5E9E55, 1, 0.5, 0.82, 0.74, 0.6, 0.66, 0.8),
         d(1, 0.5, 0.43, 0.14, 0xE8506A, 1),
         d(4, 0xE8506A, 1, 0.36, 0.44, 0.36, 0.25, 0.45, 0.35, 0.5, 0.23, 0.55, 0.35, 0.64, 0.25, 0.64, 0.44),
         d(2, 0.45, 0.4, 0.03, 0.08, 0xFF8DA0, 0.7)),
        // 7 a beach umbrella
        listOf(d(0, 0x6EC0E8, 0xA9DDF0, 0xBFE6F2), d(3, 0, 0.62, 1, 0.12, 0, 0x2E8FB8, 1), d(3, 0, 0.72, 1, 0.28, 0, 0xF2D9A6, 1),
         d(5, 0.5, 0.36, 0.56, 0.86, 0.025, 0xFFFFFF, 1),
         d(4, 0xF25C54, 1, 0.16, 0.42, 0.24, 0.31, 0.36, 0.24, 0.5, 0.2, 0.64, 0.24, 0.76, 0.31, 0.84, 0.42),
         d(4, 0xFFF4E6, 1, 0.42, 0.42, 0.44, 0.25, 0.5, 0.2, 0.56, 0.25, 0.58, 0.42),
         d(3, 0.2, 0.82, 0.28, 0.08, 0.015, 0xF7B538, 1), d(3, 0.2, 0.845, 0.28, 0.02, 0, 0xFFFFFF, 0.8)),
        // 8 golden-hour gradient
        listOf(d(0, 0xFF8A5B, 0xFFB86E, 0xFFE3A3), d(6, 0.28, 0.72, 0.45, 0xFF5E7E, 0.55), d(6, 0.78, 0.28, 0.4, 0xFFF1C2, 0.7)),
        // 9 blue-hour gradient
        listOf(d(0, 0x1F3B73, 0x4C6FB8, 0x9CB7E8), d(6, 0.72, 0.76, 0.5, 0xB78CF2, 0.65), d(6, 0.24, 0.24, 0.42, 0x7FE0E0, 0.5)),
        // 10 a date card
        listOf(d(0, 0xF4EFE6, 0xF4EFE6, 0xEAE2D3), d(7)),
        // 11 a hot-air balloon at dawn
        listOf(d(0, 0xF6BF94, 0xF6D8C0, 0xE9E3E6), d(6, 0.5, 0.95, 0.5, 0xFFE9B0, 0.6),
         d(4, 0xC9A79E, 1, 0, 0.86, 0.35, 0.78, 0.7, 0.88, 1, 0.8, 1, 1, 0, 1),
         d(1, 0.2, 0.62, 0.06, 0x4C8FD6, 1), d(5, 0.2, 0.68, 0.2, 0.71, 0.01, 0x5A4636, 1),
         d(5, 0.42, 0.5, 0.465, 0.66, 0.014, 0x5A4636, 1), d(5, 0.58, 0.5, 0.535, 0.66, 0.014, 0x5A4636, 1),
         d(1, 0.5, 0.35, 0.2, 0xE2574C, 1), d(4, 0xE2574C, 1, 0.33, 0.45, 0.5, 0.56, 0.67, 0.45),
         d(2, 0.5, 0.36, 0.075, 0.2, 0xF6C34A, 1), d(3, 0.45, 0.65, 0.1, 0.07, 0.015, 0x7A5A3E, 1)),
        // 12 a lemon tart
        listOf(d(0, 0xBFD8D2, 0xA9CBC4, 0x93BCB4), d(1, 0.52, 0.55, 0.37, 0x6F958D, 0.4), d(1, 0.5, 0.52, 0.36, 0xD9A15B, 1),
         d(1, 0.5, 0.52, 0.31, 0xF7D44C, 1), d(2, 0.42, 0.42, 0.12, 0.06, 0xFFF0A0, 0.7),
         d(1, 0.63, 0.6, 0.085, 0xFFFFFF, 1), d(1, 0.63, 0.6, 0.068, 0xF2D23A, 1),
         d(5, 0.63, 0.54, 0.63, 0.66, 0.006, 0xFFF6C8, 1), d(5, 0.57, 0.6, 0.69, 0.6, 0.006, 0xFFF6C8, 1),
         d(2, 0.39, 0.61, 0.04, 0.022, 0x5FAF6A, 1), d(2, 0.44, 0.63, 0.035, 0.02, 0x4E9A5A, 1)),
        // 13 a tent under the stars
        listOf(d(0, 0x0B1026, 0x1C2550, 0x2E2F5E), d(1, 0.2, 0.18, 0.012, 0xFFFFFF, 1), d(1, 0.42, 0.1, 0.009, 0xFFFFFF, 0.8),
         d(1, 0.66, 0.22, 0.012, 0xFFFFFF, 1), d(1, 0.84, 0.12, 0.009, 0xFFFFFF, 0.8), d(1, 0.32, 0.32, 0.008, 0xFFFFFF, 0.7),
         d(1, 0.56, 0.36, 0.01, 0xFFFFFF, 0.9), d(1, 0.9, 0.4, 0.008, 0xFFFFFF, 0.7), d(1, 0.1, 0.42, 0.008, 0xFFFFFF, 0.7),
         d(4, 0x151B3A, 1, 0, 0.72, 0.25, 0.55, 0.5, 0.7, 0.78, 0.5, 1, 0.68, 1, 1, 0, 1),
         d(3, 0, 0.84, 1, 0.16, 0, 0x0D1430, 1), d(6, 0.5, 0.78, 0.3, 0xF29E4C, 0.45),
         d(4, 0xF29E4C, 1, 0.3, 0.86, 0.5, 0.52, 0.7, 0.86), d(4, 0xFFE1A8, 1, 0.45, 0.86, 0.5, 0.66, 0.55, 0.86)),
    )

    // Lens

    /** Inverse zoom at the centre: the bulge magnifies the middle by 1 + 0.6 k. */
    fun centre(k: Double) = 1 / (1 + 0.6 * k)

    // Timeline

    fun clamp(x: Double, a: Double = 0.0, b: Double = 1.0) = min(max(x, a), b)
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun wrap(v: Double, p: Double): Double {
        val r = v % p
        return if (r < 0) r + p else r
    }

    /** v moved by whole periods to lie within half a period of 0. */
    fun nearest(v: Double, p: Double) = v - (v / p).roundHalfAway() * p

    private fun Double.roundHalfAway(): Double = if (this >= 0) floor(this + 0.5) else -floor(-this + 0.5)

    /**
     * Travel 0 → 1 over u 0 → 1 like a dolly: a soft start (the first 20 %), a steady cruise, and a
     * soft stop (the last 24 %); the speed eases in and out on half a cosine.
     */
    fun cruise(u: Double): Double {
        val a = 0.2
        val b = 0.24
        val v = 1 / (1 - a / 2 - b / 2)
        if (u <= 0) return 0.0
        if (u >= 1) return 1.0
        if (u < a) return v * (u / 2 - a / (2 * PI) * sin(PI * u / a))
        if (u > 1 - b) {
            val r = 1 - u
            return 1 - v * (r / 2 - b / (2 * PI) * sin(PI * r / b))
        }
        return v * (a / 2 + (u - a))
    }

    /** The autoPlay pose at loop time t: the pan offset from rest and the bulge strength. */
    fun auto(t: Double): FmwPose {
        // Back at rest the wall is one period on, which is rest itself: use 0 so the frames match exactly.
        val travel = cruise(seg(t, 1.0, 7.6))
        val e = if (travel >= 1) 0.0 else travel
        // The bulge swells while the wall travels, relaxes a little past rest as it stops, and settles.
        val k = 1 + 0.42 * (easeInOut(seg(t, 1.0, 2.8)) - easeInOut(seg(t, 5.6, 7.6))) -
            0.12 * sin(PI * easeInOut(seg(t, 7.0, 8.6)))
        // The focus ring around the memory under the centre fades while the wall travels.
        val ring = 1 - easeInOut(seg(t, 1.0, 1.4)) + easeInOut(seg(t, 7.8, 8.4))
        // One whole period along x (the wall repeats), with an S-shaped bob in y.
        return FmwPose(PERIOD_W * e, 90 * sin(2 * PI * e), k, ring)
    }

    /** The memory under a wall point, and how deep (pt) the point is inside it (negative in a gutter). */
    fun memory(x: Double, y: Double): Pair<Int, Double> {
        val px = wrap(x, PERIOD_W)
        val py = wrap(y, PERIOD_H)
        var best = 0
        var bestDepth = Double.NEGATIVE_INFINITY
        TILES.forEachIndexed { i, t ->
            val depth = minOf(px - t.x, t.x + t.width - px, py - t.y, t.y + t.height - py)
            if (depth > bestDepth) {
                bestDepth = depth
                best = i
            }
        }
        return best to bestDepth
    }
}

/** Touch, momentum and the return to rest. */
internal class FmwDriver {
    private enum class Phase { AUTO, TOUCH, COAST }

    private var phase = Phase.AUTO
    private var start: Double? = null
    private var lastFrame: Double? = null
    private var pose = FmwPose()
    // Drag and pinch.
    private var dragFrom = FmwPose()
    private var pinchFrom = 1.0
    private var dragging = false
    private var pinching = false
    // Momentum and return.
    private var releasePose = FmwPose()
    private var vx = 0.0
    private var vy = 0.0
    private var releaseAt = 0.0
    private var lastMemory = -1

    companion object {
        const val DECAY = 4.0
        const val IDLE = 2.0
        const val BACK = 1.2
    }

    fun pose(now: Double, auto: Boolean): FmwPose = when (phase) {
        Phase.AUTO -> {
            if (!auto) {
                FmwPose()
            } else {
                if (start == null) start = now
                // A stall (the first frame, the app in the background) pauses the clock instead of
                // skipping part of the loop.
                lastFrame?.let { if (now - it > 0.25) start = start!! + (now - it) }
                lastFrame = now
                FmwModel.auto(FmwModel.wrap(now - start!!, FmwModel.LOOP))
            }
        }
        Phase.TOUCH -> pose
        Phase.COAST -> {
            val dt = now - releaseAt
            if (dt < IDLE) {
                coast(dt)
            } else {
                val e = FmwModel.easeInOut(FmwModel.seg(dt, IDLE, IDLE + BACK))
                if (e >= 1) {
                    phase = Phase.AUTO
                    start = now
                    lastFrame = now
                    FmwPose()
                } else {
                    // Ease from where the wall drifted to, moved by whole periods to the copy of rest nearest it.
                    val from = coast(IDLE)
                    val fx = FmwModel.nearest(from.x, FmwModel.PERIOD_W)
                    val fy = FmwModel.nearest(from.y, FmwModel.PERIOD_H)
                    FmwPose(fx * (1 - e), fy * (1 - e), from.k + (1 - from.k) * e, e)
                }
            }
        }
    }

    /** The pose dt seconds after release: the throw decays exponentially. */
    private fun coast(dt: Double): FmwPose {
        val s = (1 - exp(-DECAY * dt)) / DECAY
        return FmwPose(releasePose.x + vx * s, releasePose.y + vy * s, releasePose.k, 0.0)
    }

    /** True while something still moves without autoPlay. */
    fun live(): Boolean = phase != Phase.AUTO

    private fun grab(now: Double, auto: Boolean) {
        if (phase != Phase.TOUCH) {
            val p = pose(now, auto)
            // Keep the pan small so float precision never drifts.
            pose = FmwPose(FmwModel.nearest(p.x, FmwModel.PERIOD_W), FmwModel.nearest(p.y, FmwModel.PERIOD_H), p.k, 0.0)
            phase = Phase.TOUCH
        }
    }

    fun beginDrag(now: Double, auto: Boolean) {
        grab(now, auto)
        dragFrom = pose
        dragging = true
    }

    /** Moves the wall with the finger: the tile under the centre follows it 1 : 1. True when a new memory reaches the centre. */
    fun drag(dx: Double, dy: Double): Boolean {
        val z = FmwModel.centre(pose.k)
        pose = pose.copy(x = dragFrom.x - dx * z, y = dragFrom.y - dy * z)
        return crossed()
    }

    fun beginPinch(now: Double, auto: Boolean) {
        grab(now, auto)
        pinchFrom = pose.k
        pinching = true
    }

    fun pinch(scale: Double) {
        pose = pose.copy(k = FmwModel.clamp(pinchFrom * scale, 0.25, 1.8))
    }

    fun endPinch(now: Double) {
        pinching = false
        if (!dragging) release(0.0, 0.0, now)
    }

    fun endDrag(vx: Double, vy: Double, now: Double) {
        dragging = false
        if (pinching) return
        val z = FmwModel.centre(pose.k)
        release(-vx * z, -vy * z, now)
    }

    private fun release(vx: Double, vy: Double, now: Double) {
        releasePose = pose
        this.vx = FmwModel.clamp(vx, -2400.0, 2400.0)
        this.vy = FmwModel.clamp(vy, -2400.0, 2400.0)
        releaseAt = now
        phase = Phase.COAST
    }

    private fun crossed(): Boolean {
        val (tile, depth) = FmwModel.memory(FmwModel.REST_X + pose.x, FmwModel.REST_Y + pose.y)
        if (depth <= 2 || tile == lastMemory) return false
        lastMemory = tile
        return true
    }
}
