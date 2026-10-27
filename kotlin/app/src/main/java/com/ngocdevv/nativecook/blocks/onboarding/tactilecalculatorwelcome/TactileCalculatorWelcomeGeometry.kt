package com.ngocdevv.nativecook.blocks.onboarding.tactilecalculatorwelcome

import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** A point in key plate units: the key is drawn in a 216 by 150 box. */
internal class TactileCalculatorWelcomePoint(val x: Double, val y: Double)

/** One raised bar or dot of a symbol: its top face and its visible lit and ink walls. */
internal class TactileCalculatorWelcomeGlyphPart(
    val top: Path,
    val lit: List<Path>,
    val ink: List<Path>,
)

/**
 * `keyGeometry.ts`: the isometric key, computed once. The face is a rounded square projected onto
 * the screen; a wall of its outline is visible when its outward normal points down the screen, lit
 * when it faces left and ink when it faces right. Only the body walls' top edge moves with the cap,
 * so a press only offsets numbers (`litChain`, `inkChain` and `stripes` are what the walls and the
 * pinstripes are rebuilt from).
 *
 * Paths whose numbers React Native writes with `fmt` (two decimals) are rounded the same way.
 */
internal object TactileCalculatorWelcomeGeometry {
    /** Wall height of the key body, in plate units. */
    const val DEPTH = 14.0

    /** Raised symbols stand this far above the cap. */
    private const val GLYPH_RISE = 6.5
    private const val CENTER_X = 108.0
    private const val CENTER_Y = 65.0
    private const val FACE_SIDE = 114.0
    private const val FACE_RADIUS = 31.0
    private const val BAR_LONG = 74.0
    private const val BAR = 21.0

    /** A sample of a rounded square's outline: where it is and its outward normal. */
    private class Sample(val p: TactileCalculatorWelcomePoint, val n: TactileCalculatorWelcomePoint)

    private val face: List<Sample> = roundedRect(FACE_SIDE, FACE_SIDE, FACE_RADIUS)

    /** `FACE_PATH`: the cap's top. */
    val facePath: Path = closed(face.map { iso(it.p) }, 0.0)

    /** `RING_PATH`: the thin rim ring, at ground level around the cap. */
    val ringPath: Path = closed(
        roundedRect(FACE_SIDE + 9.0, FACE_SIDE + 9.0, FACE_RADIUS + 4.5).map { iso(it.p) },
        DEPTH + 1.2,
    )

    private val bodyLit: List<TactileCalculatorWelcomePoint> = runs(face, 1)[0].map { iso(it) }
    private val bodyInk: List<TactileCalculatorWelcomePoint> = runs(face, 2)[0].map { iso(it) }

    /** `BODY_LIT_FLAT`: x, y, x, y... of the lit wall's top edge. */
    val litChain: DoubleArray = flat(bodyLit)

    /** `BODY_INK_FLAT`: x, y, x, y... of the ink wall's top edge. */
    val inkChain: DoubleArray = flat(bodyInk)

    /** `BODY_FOOT`: the silhouette's lower edge at ground level, for the outline stroke (not closed). */
    val bodyFootPath: Path = polylinePath(bodyInk + bodyLit.drop(1), DEPTH)

    /** `FRONT`: the body's front corner, where the ink wall (right) meets the lit wall (left). */
    private val front: TactileCalculatorWelcomePoint = bodyLit[0]

    /** `STRIPES`: two pinstripes catch the light just left of the front corner. */
    val stripes: List<TactileCalculatorWelcomePoint> = listOf(4.5, 9.0).map { offset ->
        val x = front.x - offset
        var y = front.y
        for (i in 1 until bodyLit.size) {
            val a = bodyLit[i - 1]
            val b = bodyLit[i]
            if ((a.x - x) * (b.x - x) <= 0.0 && a.x != b.x) {
                y = a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)
                break
            }
        }
        TactileCalculatorWelcomePoint(x, y)
    }

    /** `FACE_GLINT`: the highlight along the face's two upper rims (not closed). */
    val faceGlintPath: Path = run {
        val inner = roundedRect(FACE_SIDE - 9.0, FACE_SIDE - 9.0, FACE_RADIUS - 4.5)
        // Normals facing up the screen are the upper rims; walk them as one cyclic run.
        val up = inner.map { it.n.x + it.n.y < -0.35 }
        val first = up.indices.indexOfFirst { i -> up[i] && !up[(i - 1 + up.size) % up.size] }
        val top = ArrayList<TactileCalculatorWelcomePoint>()
        var k = 0
        while (k < inner.size && up[(first + k) % inner.size]) {
            top.add(iso(inner[(first + k) % inner.size].p))
            k += 1
        }
        polylinePath(top, 0.0)
    }

    /** `GLYPHS`: the raised symbol of each operation. */
    val glyphs: Map<TactileCalculatorWelcomeOperation, List<TactileCalculatorWelcomeGlyphPart>> = run {
        val upright = -PI / 4.0
        val diagonal = -3.0 * PI / 4.0
        mapOf(
            TactileCalculatorWelcomeOperation.Add to listOf(
                roundedRect(BAR_LONG, BAR, BAR / 2.0, upright),
                roundedRect(BAR, BAR_LONG, BAR / 2.0, upright),
            ).map { part(it) },
            TactileCalculatorWelcomeOperation.Multiply to listOf(
                roundedRect(BAR_LONG, BAR, BAR / 2.0),
                roundedRect(BAR, BAR_LONG, BAR / 2.0),
            ).map { part(it) },
            TactileCalculatorWelcomeOperation.Subtract to listOf(
                roundedRect(BAR_LONG, BAR, BAR / 2.0),
            ).map { part(it) },
            TactileCalculatorWelcomeOperation.Equals to listOf(
                roundedRect(BAR_LONG, BAR, BAR / 2.0, 0.0, 0.0, -15.0),
                roundedRect(BAR_LONG, BAR, BAR / 2.0, 0.0, 0.0, 15.0),
            ).map { part(it) },
            TactileCalculatorWelcomeOperation.Divide to listOf(
                roundedRect(BAR_LONG, BAR - 2.0, (BAR - 2.0) / 2.0, upright),
                roundedRect(20.0, 20.0, 10.0, 0.0, cos(diagonal) * 31.0, sin(diagonal) * 31.0),
                roundedRect(20.0, 20.0, 10.0, 0.0, cos(PI / 4.0) * 31.0, sin(PI / 4.0) * 31.0),
            ).map { part(it) },
        )
    }

    /** `iso`: local plane to screen, the projection the symbols always used. */
    private fun iso(p: TactileCalculatorWelcomePoint): TactileCalculatorWelcomePoint =
        TactileCalculatorWelcomePoint(
            CENTER_X + 0.94 * p.x - 0.94 * p.y,
            CENTER_Y + 0.53 * p.x + 0.53 * p.y,
        )

    /**
     * `roundedRect`: the outline of a rounded rectangle as 4 corner arcs of 13 samples each (7.5
     * degrees apart), turned by [turn] radians and moved to ([atX], [atY]).
     */
    private fun roundedRect(
        w: Double,
        h: Double,
        r: Double,
        turn: Double = 0.0,
        atX: Double = 0.0,
        atY: Double = 0.0,
    ): List<Sample> {
        val out = ArrayList<Sample>(52)
        val corners = arrayOf(
            doubleArrayOf(w / 2.0 - r, -h / 2.0 + r, -90.0),
            doubleArrayOf(w / 2.0 - r, h / 2.0 - r, 0.0),
            doubleArrayOf(-w / 2.0 + r, h / 2.0 - r, 90.0),
            doubleArrayOf(-w / 2.0 + r, -h / 2.0 + r, 180.0),
        )
        val c = cos(turn)
        val s = sin(turn)
        for (corner in corners) {
            val cx = corner[0]
            val cy = corner[1]
            val start = corner[2]
            for (i in 0..12) {
                val a = (start + i * 7.5) * PI / 180.0
                val nx = cos(a)
                val ny = sin(a)
                val x = cx + r * nx
                val y = cy + r * ny
                out.add(
                    Sample(
                        TactileCalculatorWelcomePoint(atX + x * c - y * s, atY + x * s + y * c),
                        TactileCalculatorWelcomePoint(nx * c - ny * s, nx * s + ny * c),
                    ),
                )
            }
        }
        return out
    }

    /**
     * `label`: 0 for a wall that cannot be seen (its normal does not point down the screen), 1 for a lit
     * wall (it faces left) and 2 for an ink wall (it faces right).
     */
    private fun label(sample: Sample): Int {
        val nx = sample.n.x
        val ny = sample.n.y
        if (nx + ny <= 1e-6) return 0
        return if (ny >= nx) 1 else 2
    }

    /** `runs`: the contiguous visible runs of one label, in boundary order, endpoints shared. */
    private fun runs(shape: List<Sample>, kind: Int): List<List<TactileCalculatorWelcomePoint>> {
        val labels = shape.map { label(it) }
        val count = labels.size
        var start = -1
        for (i in 0 until count) {
            if (labels[i] != kind && labels[(i + 1) % count] == kind) {
                start = i
                break
            }
        }
        if (start < 0) {
            return if (labels[0] == kind) listOf(shape.map { it.p }) else emptyList()
        }
        val result = ArrayList<List<TactileCalculatorWelcomePoint>>()
        var current: ArrayList<TactileCalculatorWelcomePoint>? = null
        for (k in 1..count) {
            val i = (start + k) % count
            if (labels[i] == kind) {
                val run = current ?: arrayListOf(shape[(i - 1 + count) % count].p)
                run.add(shape[i].p)
                current = run
            } else if (current != null) {
                current.add(shape[i].p)
                result.add(current)
                current = null
            }
        }
        if (current != null) result.add(current)
        return result
    }

    /** `part`: one bar or dot of a symbol, raised by `GLYPH_RISE`. */
    private fun part(shape: List<Sample>): TactileCalculatorWelcomeGlyphPart =
        TactileCalculatorWelcomeGlyphPart(
            top = closed(shape.map { iso(it.p) }, -GLYPH_RISE),
            lit = runs(shape, 1).map { run -> wall(run.map { iso(it) }, -GLYPH_RISE, 0.0) },
            ink = runs(shape, 2).map { run -> wall(run.map { iso(it) }, -GLYPH_RISE, 0.0) },
        )

    /** `fmt`: a number rounded to two decimals, as `Math.round(v * 100) / 100` does. */
    private fun fmt(value: Double): Float = (floor(value * 100.0 + 0.5) / 100.0).toFloat()

    private fun flat(points: List<TactileCalculatorWelcomePoint>): DoubleArray {
        val out = DoubleArray(points.size * 2)
        for (i in points.indices) {
            out[2 * i] = points[i].x
            out[2 * i + 1] = points[i].y
        }
        return out
    }

    /** `polyline`: M to the first point, L to the others, each moved down by [dy]. */
    private fun polylinePath(points: List<TactileCalculatorWelcomePoint>, dy: Double): Path {
        val path = Path()
        addPolyline(path, points, dy)
        return path
    }

    private fun addPolyline(path: Path, points: List<TactileCalculatorWelcomePoint>, dy: Double) {
        for (i in points.indices) {
            val x = fmt(points[i].x)
            val y = fmt(points[i].y + dy)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    /** `closed`: a [polylinePath] with Z. */
    private fun closed(points: List<TactileCalculatorWelcomePoint>, dy: Double): Path {
        val path = Path()
        addPolyline(path, points, dy)
        path.close()
        return path
    }

    /** `wall`: the area between the same projected chain at two heights, [top] and [bottom] below it. */
    private fun wall(chain: List<TactileCalculatorWelcomePoint>, top: Double, bottom: Double): Path {
        val path = Path()
        addPolyline(path, chain, top)
        for (i in chain.indices.reversed()) {
            path.lineTo(fmt(chain[i].x), fmt(chain[i].y + bottom))
        }
        path.close()
        return path
    }
}
