package com.ngocdevv.nativecook.blocks.text.scribbleemphasisnotes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The marks as geometry. Built once per layout (seeded, no randomness per frame), drawn every frame by the functions
// at the bottom. In doubles, like the JavaScript original, so the seeded wobble lands on the same values.

internal class P(val x: Double, val y: Double)

private class Stroke(val points: List<P>, val widths: DoubleArray, val length: Double)

/** A mark's strokes, flat: x, y, width, share of the mark drawn when the pen is here, pen (0 = moved here in the air). */
internal const val STRIDE = 5

private fun smoothstep(a: Double, b: Double, v: Double): Double {
    val u = clampTo((v - a) / (b - a), 0.0, 1.0)
    return u * u * (3 - 2 * u)
}

/** Catmull-Rom through the points. */
private fun curve(points: List<P>): List<P> {
    if (points.size < 3) return points
    val out = ArrayList<P>()
    for (i in 0 until points.size - 1) {
        val p0 = points[max(i - 1, 0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[min(i + 2, points.size - 1)]
        val steps = max(2, ceil(hypot(p2.x - p1.x, p2.y - p1.y) / (STEP * 0.5)).toInt())
        for (s in 0 until steps) {
            val u = s.toDouble() / steps
            val u2 = u * u
            val u3 = u2 * u
            out += P(
                0.5 * (2 * p1.x + (p2.x - p0.x) * u + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * u2 + (3 * p1.x - p0.x - 3 * p2.x + p3.x) * u3),
                0.5 * (2 * p1.y + (p2.y - p0.y) * u + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * u2 + (3 * p1.y - p0.y - 3 * p2.y + p3.y) * u3),
            )
        }
    }
    out += points.last()
    return out
}

/** The same line through points STEP apart. */
private fun resample(points: List<P>): List<P> {
    val out = arrayListOf(points[0])
    var carried = 0.0
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val d = hypot(b.x - a.x, b.y - a.y)
        if (d == 0.0) continue
        var at = STEP - carried
        while (at <= d) {
            out += P(mix(a.x, b.x, at / d), mix(a.y, b.y, at / d))
            at += STEP
        }
        carried = d - (at - STEP)
    }
    val last = points.last()
    val tail = out.last()
    if (hypot(last.x - tail.x, last.y - tail.y) > 0.2) out += last
    return out
}

/** A pen stroke: a hand's wobble across the line, pressure that swells in and flicks out. */
private fun ink(through: List<P>, width: Double, seed: Double, smooth: Boolean = true): Stroke {
    val line = resample(if (smooth) curve(through) else through)
    val phase = doubleArrayOf(hash(seed) * 6.28, hash(seed + 1) * 6.28, hash(seed + 2) * 6.28)
    val lengths = DoubleArray(line.size)
    for (i in 1 until line.size) lengths[i] = lengths[i - 1] + hypot(line[i].x - line[i - 1].x, line[i].y - line[i - 1].y)
    val length = max(lengths.last(), 0.001)
    // Short strokes (a dot, an arrow head) wobble less.
    val wobble = JITTER * clampTo(length / 40, 0.15, 1.0)
    val points = ArrayList<P>(line.size)
    val widths = DoubleArray(line.size)
    for (i in line.indices) {
        val before = line[max(i - 1, 0)]
        val after = line[min(i + 1, line.size - 1)]
        val d = hypot(after.x - before.x, after.y - before.y).let { if (it == 0.0) 1.0 else it }
        val s = lengths[i]
        val off = wobble * (0.6 * sin(s * 0.21 + phase[0]) + 0.4 * sin(s * 0.47 + phase[1]))
        points += P(line[i].x - (after.y - before.y) / d * off, line[i].y + (after.x - before.x) / d * off)
        val u = s / length
        // A dot is pressed, not drawn: no taper.
        val pressure = if (length < 4) {
            1.25
        } else {
            mix(TAPER_IN, 1.0, smoothstep(0.0, TAPER_IN_SPAN, u)) * mix(1.0, TAPER_OUT, smoothstep(1 - TAPER_OUT_SPAN, 1.0, u))
        }
        widths[i] = width * pressure * (1 + WIDTH_NOISE * sin(s * 0.33 + phase[2]))
    }
    return Stroke(points, widths, length)
}

/** Flattens strokes into one mark; the pen takes PEN_LIFT dp of "air" between two strokes. */
private fun pack(strokes: List<Stroke>): FloatArray {
    val total = strokes.sumOf { it.length } + PEN_LIFT * (strokes.size - 1)
    val out = FloatArray(strokes.sumOf { it.points.size } * STRIDE)
    var w = 0
    var done = 0.0
    for (stroke in strokes) {
        var along = 0.0
        for (i in stroke.points.indices) {
            if (i > 0) along += hypot(stroke.points[i].x - stroke.points[i - 1].x, stroke.points[i].y - stroke.points[i - 1].y)
            out[w++] = stroke.points[i].x.toFloat()
            out[w++] = stroke.points[i].y.toFloat()
            out[w++] = stroke.widths[i].toFloat()
            out[w++] = min((done + along) / total, 1.0).toFloat()
            out[w++] = if (i == 0) 0f else 1f
        }
        done += stroke.length + PEN_LIFT
    }
    if (out.size >= STRIDE) out[out.size - STRIDE + 3] = 1f
    return out
}

/** An ellipse round one band: a little over one turn, spiralling out so its end crosses its start. */
internal fun circleMark(band: Band, fontSize: Float, seed: Double): FloatArray {
    val x0 = band.x0.toDouble()
    val x1 = band.x1.toDouble()
    val cx = (x0 + x1) / 2
    val cy = band.base - fontSize * 0.3
    val rx = (x1 - x0) / 2 + CircleSpec.PAD_X
    val ry = fontSize * CircleSpec.RY
    val count = 72
    val points = ArrayList<P>(count + 1)
    for (i in 0..count) {
        val u = i.toDouble() / count
        val angle = CircleSpec.START + CircleSpec.TURNS * 2 * PI * u
        val r = 1 + CircleSpec.SPIRAL * (u - 0.5)
        val px = rx * r * cos(angle)
        val py = ry * r * sin(angle)
        points += P(
            cx + px * cos(CircleSpec.TILT) - py * sin(CircleSpec.TILT),
            cy + px * sin(CircleSpec.TILT) + py * cos(CircleSpec.TILT),
        )
    }
    return pack(listOf(ink(points, PEN_WIDTH, seed, smooth = false)))
}

/** A wave under each band, one stroke per line. */
internal fun underlineMark(bands: List<Band>, seed: Double): FloatArray = pack(
    bands.mapIndexed { n, band ->
        val x0 = band.x0 - Wave.OVERSHOOT
        val x1 = band.x1 + Wave.OVERSHOOT
        val points = ArrayList<P>()
        val phase = hash(seed + n * 7) * 6.28
        var x = x0
        while (x <= x1) {
            val u = (x - x0) / (x1 - x0)
            points += P(x, band.base + Wave.DROP - Wave.RISE * u + Wave.AMP * sin(2 * PI * (x - x0) / Wave.LENGTH + phase))
            x += 1.5
        }
        ink(points, PEN_WIDTH, seed + n * 13, smooth = false)
    },
)

/** A brace in the margin, from [top] to [bottom], opening to the left of [x]. */
internal fun bracketMark(x: Double, top: Double, bottom: Double, seed: Double): Pair<FloatArray, P> {
    val curl = min(Brace.CURL, (bottom - top) / 4)
    val mid = (top + bottom) / 2
    val edge = x + Brace.DEPTH
    val nose = P(edge + Brace.NOSE, mid)
    val points = listOf(
        P(x, top),
        P(edge - 1, top + curl * 0.45),
        P(edge, top + curl),
        P(edge, mid - curl),
        P(edge + Brace.NOSE * 0.5, mid - curl * 0.3),
        nose,
        P(edge + Brace.NOSE * 0.5, mid + curl * 0.3),
        P(edge, mid + curl),
        P(edge, bottom - curl),
        P(edge - 1, bottom - curl * 0.45),
        P(x, bottom),
    )
    return pack(listOf(ink(points, PEN_WIDTH, seed))) to nose
}

private class Letter(val advance: Double, val strokes: List<List<Pair<Double, Double>>>)

/** Handwriting, as strokes: x to the right, y down, 0 the top of a small letter and 1 the baseline. */
private val LETTERS: Map<Char, Letter> = mapOf(
    'e' to Letter(
        0.95,
        listOf(listOf(0.12 to 0.54, 0.5 to 0.5, 0.84 to 0.4, 0.74 to 0.1, 0.45 to 0.0, 0.16 to 0.2, 0.08 to 0.55, 0.22 to 0.88, 0.5 to 1.0, 0.88 to 0.8)),
    ),
    'x' to Letter(
        0.9,
        listOf(
            listOf(0.06 to 0.02, 0.42 to 0.48, 0.86 to 1.0),
            listOf(0.84 to 0.0, 0.48 to 0.5, 0.04 to 1.0),
        ),
    ),
    'a' to Letter(
        1.02,
        listOf(
            listOf(0.82 to 0.22, 0.6 to 0.02, 0.3 to 0.04, 0.1 to 0.36, 0.12 to 0.76, 0.36 to 0.98, 0.66 to 0.8, 0.83 to 0.34),
            listOf(0.84 to 0.02, 0.85 to 0.6, 0.9 to 0.92, 1.04 to 0.97),
        ),
    ),
    'm' to Letter(
        1.45,
        listOf(
            listOf(0.06 to 0.02, 0.08 to 0.5, 0.06 to 1.0),
            listOf(0.08 to 0.4, 0.2 to 0.1, 0.4 to 0.02, 0.58 to 0.2, 0.62 to 0.6, 0.62 to 1.0),
            listOf(0.63 to 0.4, 0.78 to 0.1, 0.98 to 0.02, 1.16 to 0.2, 1.2 to 0.6, 1.22 to 1.0),
        ),
    ),
    '!' to Letter(
        0.4,
        listOf(
            listOf(0.22 to -0.78, 0.19 to -0.1, 0.16 to 0.5),
            listOf(0.14 to 0.93, 0.17 to 1.0),
        ),
    ),
)

/** A word in that hand, its baseline starting at (x, baseline), tilted about that point. */
internal fun noteMark(word: String, x: Double, baseline: Double, seed: Double): Pair<FloatArray, Double> {
    val strokes = ArrayList<Stroke>()
    val c = cos(NoteSpec.TILT)
    val s = sin(NoteSpec.TILT)
    var pen = 0.0
    var n = 0
    for (letter in word) {
        val glyph = LETTERS[letter]
        if (glyph == null) {
            pen += 0.6
            continue
        }
        // Each letter sits a touch high or low, as a hand writes.
        val bob = (hash(seed + n * 3) - 0.5) * 0.12
        for (stroke in glyph.strokes) {
            val points = stroke.map { (gx, gy) ->
                val px = (pen + gx) * NoteSpec.X_HEIGHT
                val py = (gy - 1 + bob) * NoteSpec.X_HEIGHT
                P(x + px * c - py * s, baseline + px * s + py * c)
            }
            strokes += ink(points, NOTE_WIDTH, seed + n * 17 + strokes.size)
        }
        pen += glyph.advance + NoteSpec.GAP
        n += 1
    }
    return pack(strokes) to (pen - NoteSpec.GAP) * NoteSpec.X_HEIGHT
}

/** A curved shaft through the points and a two-stroke head at its end. */
internal fun arrowMark(through: List<P>, seed: Double): FloatArray {
    val shaft = ink(through, PEN_WIDTH, seed)
    val tip = shaft.points.last()
    val back = shaft.points[max(shaft.points.size - 4, 0)]
    val angle = atan2(back.y - tip.y, back.x - tip.x)
    val head = listOf(-Head.SPREAD, Head.SPREAD).mapIndexed { i, turn ->
        ink(
            listOf(
                P(tip.x + Head.LENGTH * cos(angle + turn), tip.y + Head.LENGTH * sin(angle + turn)),
                P(tip.x + Head.LENGTH * 0.4 * cos(angle + turn * 0.8), tip.y + Head.LENGTH * 0.4 * sin(angle + turn * 0.8)),
                tip,
            ),
            PEN_WIDTH,
            seed + 5 + i,
            smooth = false,
        )
    }
    return pack(listOf(shaft) + head)
}

/** Bands, flat: x0, x1, baseline. */
internal const val BAND_STRIDE = 3

internal fun flatBands(bands: List<Band>): FloatArray {
    val out = FloatArray(bands.size * BAND_STRIDE)
    bands.forEachIndexed { i, band ->
        out[i * BAND_STRIDE] = band.x0
        out[i * BAND_STRIDE + 1] = band.x1
        out[i * BAND_STRIDE + 2] = band.base
    }
    return out
}

// ---- Every frame ----

/** Draws [amount] (0..1) of a mark, from its start. The paint's width is set per segment: that is the taper. */
internal fun drawInk(canvas: Canvas, paint: Paint, data: FloatArray, amount: Float) {
    if (amount <= 0.0005f) return
    val count = data.size / STRIDE
    for (i in 1 until count) {
        val o = i * STRIDE
        val f0 = data[o - STRIDE + 3]
        if (f0 >= amount) break
        if (data[o + 4] == 0f) continue
        val f1 = data[o + 3]
        val x0 = data[o - STRIDE]
        val y0 = data[o - STRIDE + 1]
        var x1 = data[o]
        var y1 = data[o + 1]
        if (f1 > amount) {
            val k = (amount - f0) / (f1 - f0)
            x1 = mix(x0, x1, k)
            y1 = mix(y0, y1, k)
        }
        paint.strokeWidth = (data[o - STRIDE + 2] + data[o + 2]) / 2f
        canvas.drawLine(x0, y0, x1, y1, paint)
    }
}

/** Where a highlight's leading edge is when [amount] of it is drawn: x and baseline, written to [out]. */
internal fun bandHead(bands: FloatArray, amount: Float, out: FloatArray) {
    val count = bands.size / BAND_STRIDE
    var total = 0f
    for (i in 0 until count) total += bands[i * BAND_STRIDE + 1] - bands[i * BAND_STRIDE]
    var left = clampTo(amount, 0f, 1f) * total
    for (i in 0 until count) {
        val x0 = bands[i * BAND_STRIDE]
        val w = bands[i * BAND_STRIDE + 1] - x0
        if (left <= w || i == count - 1) {
            out[0] = x0 + min(left, w)
            out[1] = bands[i * BAND_STRIDE + 2]
            return
        }
        left -= w
    }
    out[0] = 0f
    out[1] = 0f
}

/**
 * Draws [amount] (0..1) of a highlighter swipe over the bands, line after line. The edges wobble with a seeded hash
 * of the position along the line, so the part already drawn never changes as the swipe grows.
 */
internal fun drawHighlight(canvas: Canvas, path: Path, fill: Paint, streak: Paint, bands: FloatArray, fontSize: Float, seed: Int, amount: Float) {
    if (amount <= 0.0005f) return
    val count = bands.size / BAND_STRIDE
    var total = 0f
    for (i in 0 until count) total += bands[i * BAND_STRIDE + 1] - bands[i * BAND_STRIDE]
    var left = clampTo(amount, 0f, 1f) * total
    var i = 0
    while (i < count && left > 0.01f) {
        val x0 = bands[i * BAND_STRIDE] - BandSpec.PAD_X
        val full = bands[i * BAND_STRIDE + 1] + BandSpec.PAD_X - x0
        val base = bands[i * BAND_STRIDE + 2]
        val shown = min(left / (full - 2f * BandSpec.PAD_X), 1f) * full
        left -= full - 2f * BandSpec.PAD_X
        val top = base - BandSpec.UP * fontSize
        val bottom = base + BandSpec.DOWN * fontSize
        val steps = max(1, ceil(shown / BandSpec.STEP).toInt())
        path.rewind()
        // The nib is a chisel: both ends lean.
        for (k in 0..steps) {
            val x = x0 + min(k * BandSpec.STEP, shown)
            val y = top + ((hash((seed + i * 31 + k).toDouble()) - 0.5) * 2 * BandSpec.WOBBLE).toFloat()
            if (k == 0) path.moveTo(x + BandSpec.SLANT, y) else path.lineTo(x + (if (k == steps) BandSpec.SLANT else 0f), y)
        }
        for (k in steps downTo 0) {
            val x = x0 + min(k * BandSpec.STEP, shown)
            val y = bottom + ((hash((seed + i * 31 + k + 400).toDouble()) - 0.5) * 2 * BandSpec.WOBBLE).toFloat()
            path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, fill)
        // Where the nib pressed harder, the ink is a little deeper.
        streak.strokeWidth = fontSize * 0.24f
        val stripe = base + BandSpec.DOWN * fontSize * 0.2f
        canvas.drawLine(x0 + BandSpec.SLANT, stripe, x0 + shown, stripe, streak)
        i += 1
    }
}

/** The bands words a..b cover, flat, from the measured words and lines. */
internal fun wordBands(a: Int, b: Int, page: ScribblePage): FloatArray {
    val first = page.wordLine[a]
    val last = page.wordLine[b]
    val out = FloatArray((last - first + 1) * BAND_STRIDE)
    var w = 0
    for (line in first..last) {
        out[w++] = if (line == first) page.wordX[a] else page.lineLeft[line]
        out[w++] = if (line == last) page.wordX[b] + page.wordW[b] else page.lineRight[line]
        out[w++] = page.lineBase[line]
    }
    return out
}

/** The word under a point, or the nearest one on the nearest line. */
internal fun wordAt(x: Float, y: Float, page: ScribblePage): Int {
    var line = 0
    var near = Float.POSITIVE_INFINITY
    for (i in page.lineBase.indices) {
        val d = kotlin.math.abs(page.lineBase[i] - page.fontSize * 0.32f - y)
        if (d < near) {
            near = d
            line = i
        }
    }
    var word = -1
    near = Float.POSITIVE_INFINITY
    for (i in page.wordX.indices) {
        if (page.wordLine[i] != line) continue
        val d = when {
            x < page.wordX[i] -> page.wordX[i] - x
            x > page.wordX[i] + page.wordW[i] -> x - page.wordX[i] - page.wordW[i]
            else -> 0f
        }
        if (d < near) {
            near = d
            word = i
        }
    }
    return word
}
