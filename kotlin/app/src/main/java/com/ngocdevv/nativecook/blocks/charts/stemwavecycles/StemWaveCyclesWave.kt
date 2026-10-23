package com.ngocdevv.nativecook.blocks.charts.stemwavecycles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sin

/** A block of colour along a row: its width in points and its colour, or null for a gap. */
internal class StemWaveCyclesBlock(val width: Float, val color: Color?)

/** The scene of the Stem Wave Cycles chart: its timeline and the drawing of one moment. */
internal object StemWaveCyclesWave {
    private val BLUE = Color(0xFF2E86F5)
    private val LIME = Color(0xFFE5FF00)
    private val LILAC = Color(0xFFD08BFF)
    private val GREEN = Color(0xFF1F4D22)
    private val MINT = Color(0xFFB8D4BE)
    private val WHITE = Color(0xFFFAFAFA)

    private fun b(width: Int, color: Color?) = StemWaveCyclesBlock(width.toFloat(), color)

    // Each row repeats every 520 points, so it can scroll by one pattern and land where it started.
    const val PATTERN = 520f
    private val STRIP = listOf(
        b(60, BLUE), b(90, LILAC), b(44, LIME), b(70, WHITE), b(56, MINT), b(38, GREEN), b(80, LILAC), b(42, BLUE), b(40, LIME),
    )
    /** The thinner rows that overlap the strip's top and bottom edges while the stems stand. */
    private val ROW_ABOVE = listOf(b(40, null), b(78, GREEN), b(26, BLUE), b(120, null), b(60, GREEN), b(30, BLUE), b(166, null))
    private val ROW_BELOW = listOf(
        b(90, null), b(30, BLUE), b(70, LIME), b(16, MINT), b(150, null), b(24, BLUE), b(80, LIME), b(60, null),
    )

    /** autoPlay, in seconds from the start of a loop. */
    const val LOOP = 9.0
    /** The loop rests until the wave starts to roll. */
    const val ROLL_START = 0.8
    private const val ROLL_END = 3.0
    /** Where a tap plays to: the zoomed curve, and back to the stems. */
    const val RETRACT_START = 3.0
    const val CURVE_HELD = 6.1
    const val RETURN_START = 7.1
    const val STEMS_BACK = 8.65
    private const val ROW_SHIFT = 40.0
    private const val ROW_SHIFT_FOR = 0.9
    private const val ROW_STAGGER = 0.05
    private val ROWS_OUT = 3.0 to 3.4
    private const val RETRACT_AT = RETRACT_START
    private const val STEM_FOR = 0.4
    private const val RETRACT_STAGGER = 0.025
    private val CURVE_DRAW = 3.2 to 3.9
    private val LINES_IN = 3.6 to 4.3
    private val ZOOM_IN = 4.6 to CURVE_HELD
    private val DOTS_OUT = 4.6 to 4.9
    private val MARKS_IN = 5.8 to CURVE_HELD
    private val MARKS_OUT = 7.1 to 7.3
    private val ZOOM_OUT = 7.1 to 7.9
    private val DOTS_IN = 7.7 to 7.9
    private val CURVE_UNDRAW = 7.9 to 8.4
    private val LINES_OUT = 7.9 to 8.3
    private const val GROW_AT = 7.9
    private const val GROW_STAGGER = 0.015
    private val ROWS_IN = 8.2 to 8.6
    // The strip drifts one pattern to the left over the loop, still at rest.
    private val DRIFT = ROLL_START to STEMS_BACK

    class Look(
        val width: Float,
        val height: Float,
        /** The strip's centre line. */
        val axis: Float,
        val amplitude: Float,
        val pitch: Float,
        val periodStems: Float,
        val strip: Float,
        val row: Float,
        val overlap: Float,
        val dot: Float,
        val ink: Color,
        val curveInk: Color,
        val lineInk: Color,
    )

    class Scene(
        val phase: Double,
        val zoom: Double,
        val dip: Double,
        val rows: Double,
        val rowShift: Double,
        val curve: Double,
        val curveAlpha: Double,
        val lines: Double,
        val dots: Double,
        val marks: Double,
        val drift: Double,
    )

    private fun clamp01(value: Double) = value.coerceIn(0.0, 1.0)

    private fun smooth(t: Double, range: Pair<Double, Double>): Double {
        val u = clamp01((t - range.first) / (range.second - range.first))
        return u * u * (3 - 2 * u)
    }

    private fun easeOut(value: Double): Double {
        val u = 1 - clamp01(value)
        return 1 - u * u * u
    }

    /** A short dip to 0 and back around [at], for cuts under reduced motion. */
    private fun dipAt(t: Double, at: Double) = sin(PI * smooth(t, at - 0.2 to at + 0.2))

    /**
     * The scene at a time of the loop. `phase` turns the wave (radians), `zoom` goes from a period
     * of `periodStems` stems (0) to one period across the screen (1). [still] keeps the wave from
     * rolling and the zoom from gliding, and fades instead.
     */
    fun timeline(t: Double, still: Boolean): Scene {
        val roll = smooth(t, ROLL_START to ROLL_END)
        val zoomMid = (ZOOM_IN.first + ZOOM_IN.second) / 2
        val zoomBackMid = (ZOOM_OUT.first + ZOOM_OUT.second) / 2
        val drawn = smooth(t, CURVE_DRAW) * (1 - smooth(t, CURVE_UNDRAW))
        return Scene(
            phase = if (still) 0.0 else -roll * PI * 2,
            zoom = if (still) (if (t >= zoomMid && t < zoomBackMid) 1.0 else 0.0) else smooth(t, ZOOM_IN) * (1 - smooth(t, ZOOM_OUT)),
            // Under reduced motion the zoom is a cut, hidden in a dip of the wave's opacity.
            dip = if (still) 1 - dipAt(t, zoomMid) - dipAt(t, zoomBackMid) else 1.0,
            rows = (1 - smooth(t, ROWS_OUT)) + smooth(t, ROWS_IN),
            rowShift = if (t < ROWS_OUT.second) t else -1.0,
            curve = if (still) (if (t >= CURVE_DRAW.first && t < CURVE_UNDRAW.second) 1.0 else 0.0) else drawn,
            curveAlpha = if (still) drawn else 1.0,
            lines = smooth(t, LINES_IN) * (1 - smooth(t, LINES_OUT)),
            dots = 1 - smooth(t, DOTS_OUT) + smooth(t, DOTS_IN),
            marks = smooth(t, MARKS_IN) * (1 - smooth(t, MARKS_OUT)),
            drift = if (still) 0.0 else smooth(t, DRIFT) * PATTERN,
        )
    }

    /**
     * How much of stem [i] stands at a time: 1 standing, 0 gone. While the curve takes over, each
     * stem retracts into its dot, left to right; on the way back each grows out of the strip again.
     * The second value is true while the stem hangs from its dot (retracting), false while it grows.
     */
    private fun stem(i: Int, t: Double, still: Boolean): Pair<Double, Boolean> {
        if (still) {
            val away = smooth(t, RETRACT_AT to RETRACT_AT + STEM_FOR) * (1 - smooth(t, GROW_AT to GROW_AT + STEM_FOR))
            return (1 - away) to true
        }
        if (t < GROW_AT) {
            val from = RETRACT_AT + i * RETRACT_STAGGER
            return (1 - smooth(t, from to from + STEM_FOR)) to true
        }
        return easeOut((t - GROW_AT - i * GROW_STAGGER) / STEM_FOR) to false
    }

    /** The sideways shift of block [k] of a row while the stems roll: a wipe with a stagger. */
    private fun rowShift(k: Int, t: Double): Double {
        if (t < 0) return 0.0
        // Out and back within the roll, so the rows rest where they started.
        val from = ROLL_START + k * ROW_STAGGER
        return ROW_SHIFT * sin(PI * smooth(t, from to from + ROW_SHIFT_FOR * 2))
    }

    private fun DrawScope.drawRow(
        blocks: List<StemWaveCyclesBlock>,
        y: Float,
        h: Float,
        offset: Double,
        shiftAt: Double,
        direction: Float,
        width: Float,
    ) {
        if (h <= 0.05f) return
        // Tile the pattern across the screen from the drifted start.
        var start = -(((offset % PATTERN) + PATTERN) % PATTERN).toFloat()
        while (start < width) {
            var x = start
            for (k in blocks.indices) {
                val block = blocks[k]
                val color = block.color
                if (color != null) {
                    val shift = direction * rowShift(k, shiftAt).toFloat()
                    if (x + shift < width && x + shift + block.width > 0) {
                        drawRect(color, Offset(x + shift, y), Size(block.width, h))
                    }
                }
                x += block.width
            }
            start += PATTERN
        }
    }

    /**
     * The whole picture at one moment, in points: hairlines, stems, the colour strip and its rows,
     * the curve, the dots and the crest and trough markers. Stems, dots and curve sample the same sine.
     */
    fun DrawScope.drawWave(t: Double, phase: Double, zoom: Double, still: Boolean, look: Look) {
        val s = timeline(t, still)
        val width = look.width
        val axis = look.axis
        val amplitude = look.amplitude
        val cx = width / 2
        val p0 = (look.pitch * look.periodStems).toDouble()
        // The period grows from p0 to the width; past either end the pinch keeps going a little.
        val period = p0 * (width / p0).pow(zoom)
        val spread = (period / p0).toFloat()
        fun yAt(x: Float) = axis + amplitude * sin((x - cx) / period * PI * 2 + phase).toFloat()

        // Hairlines at the full and the half amplitude, sliding in from the left.
        if (s.lines > 0) {
            for (k in intArrayOf(-2, -1, 1, 2)) {
                val y = axis + k * amplitude / 2
                drawLine(look.lineInk, Offset(0f, y), Offset(width * s.lines.toFloat(), y), 0.75f, StrokeCap.Round)
            }
        }

        // The stems, from the strip to their dots.
        val count = ceil(width / look.pitch).toInt() + 2
        val dotsAlpha = clamp01(s.dots) * s.dip
        for (i in 0 until count) {
            val x = cx + (i - (count - 1) / 2f) * look.pitch * spread
            if (x < -10 || x > width + 10) continue
            val y = yAt(x)
            val (stand, hanging) = stem(i, t, still)
            if (stand > 0.001) {
                val ink = look.ink.copy(alpha = (((if (still) stand else 1.0) * s.dip).toFloat()).coerceIn(0f, 1f))
                if (hanging) {
                    val reach = if (still) 1f else stand.toFloat()
                    drawLine(ink, Offset(x, y + (axis - y) * reach), Offset(x, y), 1f, StrokeCap.Round)
                } else {
                    drawLine(ink, Offset(x, axis), Offset(x, axis + (y - axis) * stand.toFloat()), 1f, StrokeCap.Round)
                }
            }
            // While growing back, a dot rides the top of its stem.
            val dy = if (hanging) y else axis + (y - axis) * stand.toFloat()
            if (dotsAlpha > 0.001) drawCircle(look.ink.copy(alpha = dotsAlpha.toFloat().coerceIn(0f, 1f)), look.dot, Offset(x, dy))
        }

        // The strip, and the rows over its edges while the stems stand.
        val half = look.strip / 2
        val rowHeight = look.row * clamp01(s.rows).toFloat()
        drawRow(ROW_ABOVE, axis - half - rowHeight + look.overlap, rowHeight, s.drift, s.rowShift, 1f, width)
        drawRow(ROW_BELOW, axis + half - look.overlap, rowHeight, s.drift, s.rowShift, -1f, width)
        drawRow(STRIP, axis - half, look.strip, s.drift, -1.0, 0f, width)

        // The curve, drawn from the left.
        if (s.curve > 0.001 && s.curveAlpha > 0.001) {
            val path = Path()
            val end = width * s.curve.toFloat()
            val step = 3f
            path.moveTo(0f, yAt(0f))
            var x = step
            while (x < end) {
                path.lineTo(x, yAt(x))
                x += step
            }
            path.lineTo(end, yAt(end))
            val alpha = (s.curveAlpha * s.dip).toFloat().coerceIn(0f, 1f)
            drawPath(path, look.curveInk.copy(alpha = alpha), style = Stroke(1.25f, cap = StrokeCap.Round))
        }

        // Crest and trough of the period on screen, joined to the axis.
        if (s.marks > 0.001) {
            val shiftX = -phase / (PI * 2) * period
            var crest = cx - period / 4 + shiftX
            while (crest < cx - period / 2) crest += period
            while (crest > cx + period / 2) crest -= period
            var trough = crest + period / 2
            if (trough > cx + period / 2) trough -= period
            val ink = look.ink.copy(alpha = (s.marks * s.dip).toFloat().coerceIn(0f, 1f))
            for (mark in doubleArrayOf(crest, trough)) {
                val x = mark.toFloat()
                val y = yAt(x)
                drawLine(ink, Offset(x, axis), Offset(x, axis + (y - axis) * s.marks.toFloat()), 1f, StrokeCap.Round)
                drawCircle(ink, look.dot, Offset(x, y))
                drawCircle(ink, look.dot * 0.8f, Offset(x, axis))
            }
        }
    }
}
