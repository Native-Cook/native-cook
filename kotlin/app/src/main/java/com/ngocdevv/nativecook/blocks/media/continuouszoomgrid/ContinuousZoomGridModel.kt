package com.ngocdevv.nativecook.blocks.media.continuouszoomgrid

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 The numbers behind Continuous Zoom Grid (a port of ContinuousZoomGridModel.swift; the numbers are
 the same): the library's 3,000 drawn photos (each one generated from its index), its months, the
 zoom levels, and the autoPlay timeline.
 */

internal class CzgRGB(val r: Double, val g: Double, val b: Double) {
    fun scaled(k: Double) = CzgRGB(r * k, g * k, b * k)

    companion object {
        fun mix(a: CzgRGB, b: CzgRGB, t: Double) = CzgRGB(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t)
    }
}

/** The screen's view of the library: the zoom (photos per row), the photo it is anchored on and that photo's centre on screen (y). */
internal class CzgView(val z: Double, val anchor: Int, val fy: Double)

internal sealed class CzgOp {
    class Rect(val x: Double, val y: Double, val w: Double, val h: Double, val c: CzgRGB, val a: Double) : CzgOp()
    /** A vertical gradient filling a rect, top colour then bottom colour. */
    class Grad(val x: Double, val y: Double, val w: Double, val h: Double, val top: CzgRGB, val bottom: CzgRGB) : CzgOp()
    class Circle(val x: Double, val y: Double, val r: Double, val c: CzgRGB, val a: Double) : CzgOp()
    /** A soft light: a radial gradient from the colour (at the given opacity) to clear. */
    class Glow(val x: Double, val y: Double, val r: Double, val c: CzgRGB, val a: Double) : CzgOp()
    class Poly(val pts: DoubleArray, val c: CzgRGB, val a: Double) : CzgOp()
}

internal class CzgPhoto(val ops: List<CzgOp>, /** Seconds, for a video; 0 for a still. */ val duration: Int)

internal object CzgModel {
    const val COUNT = 3000
    /** The layouts the zoom blends between, in photos per row. */
    val levels = doubleArrayOf(3.0, 5.0, 9.0, 14.0, 20.0)
    const val MIN_Z = 3.0
    const val MAX_Z = 20.0
    const val LOOP = 8.0

    /** A small integer hash in 0…1, the same in every language. */
    fun hash(a: Int, b: Int, c: Int): Double {
        var x = (a * 73_856_093) xor (b * 19_349_663) xor (c * 83_492_791)
        x = x xor (x ushr 13)
        x *= 1_274_126_177
        x = x xor (x ushr 16)
        return (x and 0xFFFF) / 65535.0
    }

    // MARK: Photos

    class Palette(val top: CzgRGB, val bottom: CzgRGB, val ground: CzgRGB, val accent: CzgRGB)
    private fun c(r: Double, g: Double, b: Double) = CzgRGB(r, g, b)
    val palettes = listOf(
        Palette(c(0.98, 0.56, 0.38), c(1.0, 0.84, 0.60), c(0.34, 0.20, 0.26), c(1.0, 0.94, 0.72)), // golden hour
        Palette(c(0.20, 0.46, 0.80), c(0.70, 0.86, 0.96), c(0.10, 0.28, 0.42), c(1.0, 0.98, 0.92)), // sea
        Palette(c(0.58, 0.76, 0.70), c(0.90, 0.94, 0.84), c(0.14, 0.32, 0.22), c(1.0, 0.90, 0.56)), // forest
        Palette(c(0.46, 0.40, 0.78), c(0.96, 0.72, 0.80), c(0.24, 0.18, 0.40), c(1.0, 0.88, 0.80)), // lavender
        Palette(c(0.90, 0.72, 0.56), c(0.99, 0.90, 0.76), c(0.72, 0.46, 0.30), c(1.0, 0.62, 0.36)), // desert
        Palette(c(0.04, 0.06, 0.16), c(0.20, 0.14, 0.34), c(0.05, 0.05, 0.10), c(1.0, 0.76, 0.42)), // night
        Palette(c(0.94, 0.54, 0.62), c(1.0, 0.86, 0.78), c(0.52, 0.24, 0.34), c(1.0, 0.96, 0.88)), // rose
        Palette(c(0.56, 0.62, 0.68), c(0.88, 0.89, 0.90), c(0.22, 0.25, 0.28), c(0.98, 0.96, 0.90)), // overcast
    )

    /** Photos come in runs, like a library's days out: most share their run's kind (runs of 4) and palette (runs of 8). */
    fun kind(i: Int) = (hash(if (hash(i, 8, 0) < 0.4) i / 4 else i + 5000, 2, 0) * 7.999).toInt()
    fun palette(i: Int): Palette {
        // Lights out of focus are always shot at night or at dusk.
        if (kind(i) == 2) return palettes[if (hash(i, 1, 0) < 0.6) 5 else 3]
        return palettes[(hash(if (hash(i, 9, 0) < 0.6) i / 8 else i + 5000, 1, 0) * 7.999).toInt()]
    }

    /** A band filled below a wavy line: y = base + amp · sin(2π · (freq · x + phase)), 13 points. */
    fun wave(base: Double, amp: Double, freq: Double, phase: Double): DoubleArray {
        val pts = ArrayList<Double>()
        for (k in 0..12) {
            val x = k / 12.0
            pts.add(x)
            pts.add(base + amp * sin(2 * PI * (freq * x + phase)))
        }
        pts.addAll(listOf(1.0, 1.0, 0.0, 1.0))
        return pts.toDoubleArray()
    }

    /** The photo at an index: a palette, one of eight kinds of picture and a few shape parameters, all from the hash of the index. */
    fun photo(i: Int): CzgPhoto {
        val p = palette(i)
        val a = hash(i, 3, 0)
        val b = hash(i, 4, 0)
        val cc = hash(i, 7, 0)
        val ops = ArrayList<CzgOp>()
        when (kind(i)) {
            0 -> { // Sun over the sea.
                val sx = 0.25 + 0.5 * a
                val h = 0.56 + 0.1 * b
                ops.add(CzgOp.Grad(0.0, 0.0, 1.0, h, p.top, p.bottom))
                ops.add(CzgOp.Glow(sx, h - 0.04, 0.42, p.accent, 0.7))
                ops.add(CzgOp.Circle(sx, h - 0.03, 0.065, p.accent, 1.0))
                ops.add(CzgOp.Grad(0.0, h, 1.0, 1 - h, p.ground.scaled(1.4), p.ground.scaled(0.7)))
                for (k in 0 until 4) {
                    val wk = 0.2 - 0.04 * k
                    ops.add(CzgOp.Rect(sx - wk / 2, h + 0.04 + 0.07 * k, wk, 0.018, p.accent, 0.7 - 0.12 * k))
                }
            }
            1 -> { // Hills fading into haze.
                ops.add(CzgOp.Grad(0.0, 0.0, 1.0, 1.0, p.top, p.bottom))
                ops.add(CzgOp.Glow(0.2 + 0.6 * a, 0.3, 0.5, p.accent, 0.45))
                for (k in 0 until 4) {
                    val t = k / 3.0
                    ops.add(
                        CzgOp.Poly(
                            wave(0.42 + 0.14 * k, 0.05 + 0.02 * b, 0.8 + 0.5 * (k % 2), a + 0.3 * k),
                            CzgRGB.mix(CzgRGB.mix(p.bottom, p.ground, 0.35), p.ground.scaled(0.75), t), 1.0,
                        ),
                    )
                }
            }
            2 -> { // City lights out of focus.
                ops.add(CzgOp.Grad(0.0, 0.0, 1.0, 1.0, p.top, p.bottom))
                for (k in 0 until 9) {
                    val col = if (k % 3 == 0) p.bottom.scaled(1.6) else p.accent
                    ops.add(
                        CzgOp.Glow(
                            hash(i, 10 + k, 1), 0.15 + 0.8 * hash(i, 10 + k, 2), 0.07 + 0.12 * hash(i, 10 + k, 3), col,
                            0.55 + 0.35 * hash(i, 10 + k, 4),
                        ),
                    )
                }
            }
            3 -> { // Dunes.
                ops.add(CzgOp.Grad(0.0, 0.0, 1.0, 1.0, p.top, p.bottom))
                ops.add(CzgOp.Glow(0.3 + 0.4 * a, 0.22, 0.3, p.accent, 0.5))
                for (k in 0 until 3) {
                    ops.add(CzgOp.Poly(wave(0.5 + 0.16 * k, 0.06, 0.5 + 0.25 * k, b + 0.37 * k), CzgRGB.mix(p.accent.scaled(0.9), p.ground, 0.35 + 0.3 * k), 1.0))
                }
            }
            4 -> { // Pines in mist.
                ops.add(CzgOp.Grad(0.0, 0.0, 1.0, 1.0, p.bottom, p.top.scaled(0.9)))
                for (row in 0 until 2) {
                    val base = 0.62 + 0.22 * row
                    val col = CzgRGB.mix(p.top, p.ground.scaled(0.6), 0.45 + 0.5 * row)
                    for (k in 0 until 7) {
                        val x = (k + 0.5 * row + 0.3 * hash(i, k, row)) / 6.5
                        val hgt = 0.26 + 0.14 * hash(i, k + 9, row)
                        ops.add(CzgOp.Poly(doubleArrayOf(x - 0.07, base, x, base - hgt, x + 0.07, base), col, 1.0))
                    }
                    ops.add(CzgOp.Rect(0.0, base - 0.01, 1.0, 1.0, col, 1.0))
                    if (row == 0) ops.add(CzgOp.Rect(0.0, base - 0.16, 1.0, 0.5, p.bottom, 0.45))
                }
            }
            5 -> { // Petals, close up.
                ops.add(CzgOp.Grad(0.0, 0.0, 1.0, 1.0, p.ground.scaled(0.9), p.ground.scaled(0.5)))
                for (k in 0 until 6) {
                    val col = listOf(p.accent, p.top, p.bottom)[k % 3]
                    ops.add(CzgOp.Glow(hash(i, 20 + k, 1), hash(i, 20 + k, 2), 0.22 + 0.2 * hash(i, 20 + k, 3), col, 0.75))
                }
            }
            6 -> { // A building in low sun.
                val x = 0.16 + 0.3 * a
                val top = 0.18 + 0.2 * b
                ops.add(CzgOp.Grad(0.0, 0.0, 1.0, 1.0, p.top, p.bottom))
                ops.add(CzgOp.Glow(1 - a, 0.2, 0.4, p.accent, 0.5))
                ops.add(CzgOp.Rect(x, top, 0.4, 1.0, CzgRGB.mix(p.bottom, p.accent, 0.5), 1.0))
                ops.add(CzgOp.Poly(doubleArrayOf(x + 0.4, top, x + 0.56, top + 0.08, x + 0.56, 1.0, x + 0.4, 1.0), p.ground.scaled(1.3), 1.0))
                for (r in 0 until 6) for (q in 0 until 3) {
                    ops.add(CzgOp.Rect(x + 0.05 + q * 0.12, top + 0.06 + r * 0.12, 0.07, 0.07, p.ground, 0.55))
                }
            }
            else -> { // A beach.
                val h = 0.46 + 0.08 * cc
                ops.add(CzgOp.Grad(0.0, 0.0, 1.0, h, p.top, p.bottom))
                ops.add(CzgOp.Glow(0.2 + 0.6 * a, h - 0.1, 0.3, p.accent, 0.45))
                ops.add(CzgOp.Grad(0.0, h, 1.0, 0.2, p.top.scaled(0.8), p.top.scaled(1.1)))
                ops.add(CzgOp.Poly(wave(h + 0.18, 0.025, 1.4, b), p.accent.scaled(0.97), 1.0))
                ops.add(CzgOp.Poly(wave(h + 0.22, 0.03, 1.4, b + 0.05), CzgRGB.mix(p.accent, p.ground, 0.25), 1.0))
            }
        }
        val duration = if (hash(i, 5, 0) > 0.84) 3 + (hash(i, 6, 0) * 55).toInt() else 0
        return CzgPhoto(ops, duration)
    }

    /** What a photo shows when it is too small for its picture: its sky over its ground, split at a height (1 for one colour). */
    fun tiny(i: Int): Triple<CzgRGB, CzgRGB, Double> {
        val p = palette(i)
        val sky = CzgRGB.mix(p.top, p.bottom, 0.5)
        return when (kind(i)) {
            0 -> Triple(sky, p.ground, 0.6)
            1 -> Triple(sky, CzgRGB.mix(p.ground, p.bottom, 0.4), 0.5)
            2 -> Triple(CzgRGB.mix(p.top, p.bottom, 0.4), sky, 1.0)
            3 -> Triple(sky, CzgRGB.mix(p.accent.scaled(0.9), p.ground, 0.5), 0.55)
            4 -> Triple(p.bottom, CzgRGB.mix(p.top, p.ground.scaled(0.6), 0.7), 0.55)
            5 -> Triple(CzgRGB.mix(p.ground.scaled(0.7), p.accent, 0.25), sky, 1.0)
            6 -> Triple(sky, CzgRGB.mix(p.bottom, p.accent, 0.5), 0.45)
            else -> Triple(sky, CzgRGB.mix(p.accent, p.ground, 0.2), 0.62)
        }
    }

    // MARK: Months

    /** The first index of each month, newest first, and its label. */
    val monthStarts: IntArray
    val monthLabels: List<String>

    init {
        val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val starts = ArrayList<Int>()
        val labels = ArrayList<String>()
        var i = 0
        var m = 9
        var y = 2026
        while (i < COUNT) {
            starts.add(i)
            labels.add("${names[m]} $y")
            i += 30 + (hash(m, y, 9) * 80).toInt()
            m -= 1
            if (m < 0) {
                m = 11
                y -= 1
            }
        }
        monthStarts = starts.toIntArray()
        monthLabels = labels
    }
    val monthStartSet = monthStarts.toHashSet()

    fun month(i: Int): String {
        var lo = 0
        var hi = monthStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (monthStarts[mid] <= i) lo = mid else hi = mid - 1
        }
        return monthLabels[lo]
    }

    // MARK: Easing

    fun clamp01(x: Double) = min(1.0, max(0.0, x))
    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    fun easeInOut(x: Double): Double {
        val u = clamp01(x)
        return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
    }
    /** Ease in (cubic) for the first half, then out with a small overshoot (about 5 %). */
    fun easeInOutBack(x: Double): Double {
        val u = clamp01(x)
        if (u < 0.5) return 4 * u * u * u
        val v = 2 * u - 1
        val c1 = 1.2
        val c3 = c1 + 1
        return 0.5 + 0.5 * (1 + c3 * (v - 1).pow(3) + c1 * (v - 1).pow(2))
    }

    // MARK: autoPlay

    /** The photo the screen rests on (its centre at half the screen's height). */
    const val REST_ANCHOR = 901
    /** The photo the zoom comes back in on: two rows lower, in the left column at 3 per row. */
    const val RETURN_ANCHOR = 906

    fun row(i: Int, n: Int) = i / n
    fun col(i: Int, n: Int) = i % n

    /**
     * autoPlay (LOOP = 8 s): rest at 3 per row; 1.0 – 2.8 zoom out to 20 per row about the middle
     * photo; hold; 3.4 – 4.4 the grid is dragged down until the return photo sits where it will land;
     * 4.6 – 6.4 zoom back in to 3 per row about that photo, landing with a small overshoot; rest to
     * 8.0 (the same frame as 0).
     */
    fun view(time: Double, w: Double, h: Double): CzgView {
        var t = time % LOOP
        if (t < 0) t += LOOP
        val restFy = h * 0.5
        // Where the return photo sits at rest (it lands there again).
        val target = restFy + (row(RETURN_ANCHOR, 3) - row(REST_ANCHOR, 3)) * w / 3
        // At 20 per row both photos share a row, so dragging moves the return photo to its target.
        val dense = MAX_Z
        val rowGap = (row(RETURN_ANCHOR, dense.toInt()) - row(REST_ANCHOR, dense.toInt())) * w / dense
        if (t < 1.0) return CzgView(3.0, REST_ANCHOR, restFy)
        if (t < 2.8) {
            val u = easeInOut((t - 1.0) / 1.8)
            return CzgView(3 * (dense / 3).pow(u), REST_ANCHOR, restFy)
        }
        if (t < 4.5) {
            val u = easeInOut((t - 3.4) / 1.0)
            return CzgView(dense, REST_ANCHOR, restFy + (target - rowGap - restFy) * u)
        }
        if (t < 6.4) {
            val u = easeInOutBack((t - 4.6) / 1.8)
            return CzgView(dense * (3 / dense).pow(u), RETURN_ANCHOR, target)
        }
        return CzgView(3.0, REST_ANCHOR, restFy)
    }
}
