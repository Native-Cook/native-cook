package com.ngocdevv.nativecook.blocks.playground.neonsignflicker

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// The bent glass of the sign: every tube as a smooth curve, defined by hand (a port of the React
// Native block's neonSignTubes.ts, number for number).
//
// The script is not set from a font. Each letter is one or two tubes, each tube a list of knots
// (in x-height units: baseline at y = 0, the top of the x-height at y = -1, y down) that a
// centripetal Catmull-Rom spline runs through, the way a glass bender heats and bends a tube
// through a few points. The letters are then slanted and placed on the sign. The cocktail glass
// is a polyline with rounded bends, since tubes cannot take a sharp corner.
//
// A tube becomes a list of cubic segments `[x0, y0, c1x, c1y, c2x, c2y, x1, y1]` in sign points.

/** One lit piece of the sign: a letter, a part of the glass, or a swash. */
internal class NeonPiece(
    /** The word (or icon) it belongs to: 0 = "open", 1 = "late", 2 = the glass. */
    val word: Int,
    /** Index into the colour table of the sign: 0 rose, 1 ice cyan, 2 amber, 3 lime. */
    val tone: Int,
    /** Cubic segments per tube, in sign points (origin at the sign's centre). */
    var tubes: List<DoubleArray>,
    /** Its box, in sign points: x, y, width, height. */
    var box: DoubleArray,
    /** Order in which it lights within its word, from 0. */
    val order: Int,
    /** Whether the glass is blacked out at the start and at the end of its first tube (electrodes). */
    val endStart: Boolean,
    val endEnd: Boolean,
)

internal class NeonSign(val pieces: List<NeonPiece>, val width: Double, val height: Double)

internal object NeonSignTubes {
    /** The size of one x-height unit on the sign, in dp. */
    const val UNIT = 41.0
    // Script slant: a knot one x-height up moves this far right.
    private const val SLANT = 0.24

    private class Glyph(val tubes: List<List<DoubleArray>>, val advance: Double)

    private fun k(x: Double, y: Double) = doubleArrayOf(x, y)

    // Lowercase script, monoline, each letter starting near the baseline on the left and leaving
    // on the right towards the next one.
    private val glyphs: Map<Char, Glyph> = mapOf(
        'o' to Glyph(
            listOf(
                listOf(
                    k(0.62, -0.98), k(0.3, -0.98), k(0.07, -0.72), k(0.04, -0.3), k(0.24, -0.02), k(0.52, -0.05),
                    k(0.72, -0.36), k(0.71, -0.76), k(0.6, -0.96), k(0.49, -0.93), k(0.56, -0.83), k(0.78, -0.84),
                    k(1.0, -0.92),
                ),
            ),
            1.02,
        ),
        'p' to Glyph(
            listOf(
                listOf(k(0.0, -0.9), k(0.13, -1.03), k(0.22, -0.9), k(0.18, -0.3), k(0.1, 0.36), k(0.04, 0.8)),
                listOf(k(0.19, -0.6), k(0.38, -0.92), k(0.64, -1.0), k(0.84, -0.78), k(0.8, -0.34), k(0.54, -0.04), k(0.3, -0.06), k(0.2, -0.2)),
            ),
            0.98,
        ),
        'e' to Glyph(
            listOf(
                listOf(
                    k(0.0, -0.22), k(0.3, -0.42), k(0.56, -0.66), k(0.58, -0.92), k(0.38, -1.0), k(0.14, -0.82),
                    k(0.06, -0.42), k(0.2, -0.06), k(0.48, 0.0), k(0.76, -0.2),
                ),
            ),
            0.82,
        ),
        'n' to Glyph(
            listOf(
                listOf(
                    k(0.0, -0.2), k(0.1, -0.62), k(0.2, -0.98), k(0.28, -0.92), k(0.26, -0.5), k(0.22, -0.04),
                    k(0.29, -0.55), k(0.48, -0.92), k(0.7, -0.98), k(0.84, -0.76), k(0.84, -0.3), k(0.9, -0.04),
                    k(1.06, 0.0), k(1.2, -0.16),
                ),
            ),
            1.22,
        ),
        'l' to Glyph(
            listOf(
                listOf(
                    k(0.0, -0.18), k(0.3, -0.62), k(0.56, -1.2), k(0.62, -1.66), k(0.5, -1.9), k(0.34, -1.76),
                    k(0.26, -1.2), k(0.22, -0.5), k(0.28, -0.06), k(0.5, 0.0), k(0.7, -0.2),
                ),
            ),
            0.72,
        ),
        'a' to Glyph(
            listOf(
                listOf(
                    k(0.0, -0.22), k(0.3, -0.64), k(0.64, -0.9), k(0.46, -1.0), k(0.2, -0.86), k(0.06, -0.46),
                    k(0.14, -0.06), k(0.38, -0.02), k(0.6, -0.32), k(0.72, -0.72), k(0.8, -1.0), k(0.74, -0.56),
                    k(0.72, -0.12), k(0.86, 0.0), k(1.06, -0.18),
                ),
            ),
            1.1,
        ),
        't' to Glyph(
            listOf(
                listOf(k(0.0, -0.2), k(0.22, -0.7), k(0.4, -1.42), k(0.47, -1.5), k(0.43, -1.0), k(0.35, -0.4), k(0.39, -0.04), k(0.58, 0.0), k(0.78, -0.2)),
                listOf(k(0.12, -0.98), k(0.44, -1.04), k(0.82, -1.0)),
            ),
            0.8,
        ),
    )

    /** Centripetal Catmull-Rom (alpha 0.5) through the knots, as cubic segments. */
    private fun spline(points: List<DoubleArray>): DoubleArray {
        val out = ArrayList<Double>()
        val n = points.size
        fun at(i: Int) = points[max(0, min(n - 1, i))]
        for (i in 0 until n - 1) {
            val p0 = at(i - 1)
            val p1 = at(i)
            val p2 = at(i + 1)
            val p3 = at(i + 2)
            val d1 = max(1e-4, sqrt(hypot(p1[0] - p0[0], p1[1] - p0[1])))
            val d2 = max(1e-4, sqrt(hypot(p2[0] - p1[0], p2[1] - p1[1])))
            val d3 = max(1e-4, sqrt(hypot(p3[0] - p2[0], p3[1] - p2[1])))
            // At the ends the missing neighbour is the knot itself: the curve leaves along the chord.
            val c1x: Double
            val c1y: Double
            val c2x: Double
            val c2y: Double
            if (i == 0) {
                c1x = p1[0] + (p2[0] - p1[0]) / 3
                c1y = p1[1] + (p2[1] - p1[1]) / 3
            } else {
                val a = d1 * d1
                val b = d2 * d2
                val kk = 2 * a + 3 * d1 * d2 + b
                val m = 3 * d1 * (d1 + d2)
                c1x = (a * p2[0] - b * p0[0] + kk * p1[0]) / m
                c1y = (a * p2[1] - b * p0[1] + kk * p1[1]) / m
            }
            if (i == n - 2) {
                c2x = p2[0] - (p2[0] - p1[0]) / 3
                c2y = p2[1] - (p2[1] - p1[1]) / 3
            } else {
                val a = d3 * d3
                val b = d2 * d2
                val kk = 2 * a + 3 * d3 * d2 + b
                val m = 3 * d3 * (d3 + d2)
                c2x = (a * p1[0] - b * p3[0] + kk * p2[0]) / m
                c2y = (a * p1[1] - b * p3[1] + kk * p2[1]) / m
            }
            out.addAll(listOf(p1[0], p1[1], c1x, c1y, c2x, c2y, p2[0], p2[1]))
        }
        return out.toDoubleArray()
    }

    /** A polyline whose corners are bent round with radius `r` (same units), as cubic segments. */
    private fun bentPolyline(points: List<DoubleArray>, r: Double): DoubleArray {
        val out = ArrayList<Double>()
        fun line(ax: Double, ay: Double, bx: Double, by: Double) {
            out.addAll(listOf(ax, ay, ax + (bx - ax) / 3, ay + (by - ay) / 3, ax + 2 * (bx - ax) / 3, ay + 2 * (by - ay) / 3, bx, by))
        }
        var x = points[0][0]
        var y = points[0][1]
        for (i in 1 until points.size) {
            val cx = points[i][0]
            val cy = points[i][1]
            if (i == points.size - 1) {
                line(x, y, cx, cy)
                break
            }
            val nx = points[i + 1][0]
            val ny = points[i + 1][1]
            val inLen = hypot(cx - x, cy - y)
            val outLen = hypot(nx - cx, ny - cy)
            val t = min(r, min(inLen / 2, outLen / 2))
            val ax = cx - (cx - x) / inLen * t
            val ay = cy - (cy - y) / inLen * t
            val bx = cx + (nx - cx) / outLen * t
            val by = cy + (ny - cy) / outLen * t
            line(x, y, ax, ay)
            // A quarter-ish bend: a cubic pulled towards the corner (0.55 of the way, close to a circle).
            out.addAll(listOf(ax, ay, ax + (cx - ax) * 0.55, ay + (cy - ay) * 0.55, bx + (cx - bx) * 0.55, by + (cy - by) * 0.55, bx, by))
            x = bx
            y = by
        }
        return out.toDoubleArray()
    }

    private fun transformed(segments: DoubleArray, scale: Double, dx: Double, dy: Double, slant: Double): DoubleArray {
        val out = DoubleArray(segments.size)
        var i = 0
        while (i < segments.size) {
            val x = segments[i]
            val y = segments[i + 1]
            out[i] = (x - y * slant) * scale + dx
            out[i + 1] = y * scale + dy
            i += 2
        }
        return out
    }

    fun boxOf(tubes: List<DoubleArray>): DoubleArray {
        var x0 = Double.POSITIVE_INFINITY
        var y0 = Double.POSITIVE_INFINITY
        var x1 = Double.NEGATIVE_INFINITY
        var y1 = Double.NEGATIVE_INFINITY
        for (t in tubes) {
            var i = 0
            while (i < t.size) {
                x0 = min(x0, t[i])
                x1 = max(x1, t[i])
                y0 = min(y0, t[i + 1])
                y1 = max(y1, t[i + 1])
                i += 2
            }
        }
        return doubleArrayOf(x0, y0, x1 - x0, y1 - y0)
    }

    /** Sets a word in script from `x`, `y` (baseline, in units); returns its pieces. */
    private fun setWord(text: String, word: Int, tone: Int, x: Double, y: Double): List<NeonPiece> {
        val pieces = ArrayList<NeonPiece>()
        var pen = x
        for (i in text.indices) {
            val g = glyphs.getValue(text[i])
            val tubes = g.tubes.map { transformed(spline(it), UNIT, pen * UNIT, y * UNIT, SLANT) }
            pieces.add(NeonPiece(word, tone, tubes, boxOf(tubes), i, i == 0, i == text.length - 1))
            // Letters overlap a little, so each one's lead-in meets the last one's exit.
            pen += g.advance - 0.04
        }
        return pieces
    }

    /** The cocktail glass and its olive, centred on `x`, `y` (units). */
    private fun setGlass(word: Int, x: Double, y: Double): List<NeonPiece> {
        val s = UNIT
        fun place(segments: DoubleArray) = transformed(segments, s, x * s, y * s, 0.0)
        val bowl = place(bentPolyline(listOf(k(-0.56, 1.02), k(0.0, 0.95), k(0.0, 0.06), k(1.02, -1.08), k(-1.02, -1.08), k(-0.04, 0.02)), 0.16))
        val foot = place(bentPolyline(listOf(k(0.06, 0.95), k(0.58, 1.02)), 0.1))
        // The liquid's surface, a slow wave across the bowl.
        val drink = place(spline(listOf(k(-0.7, -0.74), k(-0.36, -0.68), k(0.0, -0.76), k(0.34, -0.7), k(0.68, -0.76))))
        val olive = place(spline(listOf(k(-0.2, -0.82), k(-0.42, -0.74), k(-0.46, -0.52), k(-0.28, -0.4), k(-0.08, -0.48), k(-0.06, -0.7), k(-0.2, -0.82))))
        val pick = place(bentPolyline(listOf(k(0.42, -1.55), k(-0.02, -0.86)), 0.1))
        return listOf(
            NeonPiece(word, 2, listOf(bowl, foot), boxOf(listOf(bowl, foot)), 0, true, true),
            NeonPiece(word, 2, listOf(drink), boxOf(listOf(drink)), 1, true, true),
            NeonPiece(word, 3, listOf(olive, pick), boxOf(listOf(olive, pick)), 2, false, false),
        )
    }

    /** The whole sign, centred on its own origin: "open" over "late", the glass to the right. */
    fun buildSign(): NeonSign {
        val swash = transformed(spline(listOf(k(1.3, 3.06), k(2.2, 2.92), k(3.3, 3.04), k(4.4, 2.9), k(5.3, 2.98))), UNIT, 0.0, 0.0, 0.0)
        val pieces = ArrayList<NeonPiece>()
        pieces.addAll(setWord("open", 0, 0, 0.0, 0.0))
        pieces.addAll(setWord("late", 1, 1, 1.5, 2.45))
        pieces.addAll(setGlass(2, 5.35, -0.5))
        pieces.add(NeonPiece(1, 1, listOf(swash), boxOf(listOf(swash)), 4, true, true))
        // Centre everything on the origin.
        val all = boxOf(pieces.flatMap { it.tubes })
        val cx = all[0] + all[2] / 2
        val cy = all[1] + all[3] / 2
        for (p in pieces) {
            p.tubes = p.tubes.map { t -> DoubleArray(t.size) { i -> if (i % 2 == 0) t[i] - cx else t[i] - cy } }
            p.box = boxOf(p.tubes)
        }
        return NeonSign(pieces, all[2], all[3])
    }
}
