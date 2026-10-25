package com.ngocdevv.nativecook.blocks.forms.icingnameform

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 A monoline piping alphabet (as in React Native): every letter is the path a decorator's nozzle
 follows, written as strokes in font units (x-height 1, baseline 0, y down; capitals 1.45 tall).
 Commands: M x y · L x y · Q cx cy x y · C c1x c1y c2x c2y x y · A cx cy rx ry startDeg sweepDeg.
 Each M starts a new stroke. The layout samples the strokes into polylines in pt, slants them like
 hand lettering and adds a tiny seeded wobble.
*/

private val G = mapOf(
    'a' to (1.1f to "A 0.46 -0.5 0.44 0.5 -25 -335 M 0.93 -1 L 0.93 -0.12 Q 0.95 0.02 1.08 -0.04"),
    'b' to (1.0f to "M 0.08 -1.5 L 0.08 0 M 0.08 -0.5 A 0.52 -0.5 0.44 0.5 180 359"),
    'c' to (0.95f to "A 0.5 -0.5 0.46 0.5 -40 -280"),
    'd' to (1.05f to "A 0.45 -0.5 0.44 0.5 0 359 M 0.92 -1.5 L 0.92 0"),
    'e' to (1.0f to "M 0.08 -0.5 L 0.93 -0.5 A 0.5 -0.5 0.44 0.5 0 -315"),
    'f' to (0.8f to "M 0.85 -1.38 C 0.72 -1.55 0.36 -1.52 0.36 -1.18 L 0.36 0 M 0.05 -0.98 L 0.74 -0.98"),
    'g' to (1.05f to "A 0.45 -0.5 0.43 0.5 0 359 M 0.9 -1 L 0.9 0.22 C 0.9 0.62 0.3 0.66 0.08 0.36"),
    'h' to (1.0f to "M 0.08 -1.5 L 0.08 0 M 0.08 -0.62 C 0.18 -1.06 0.86 -1.1 0.86 -0.56 L 0.86 0"),
    'i' to (0.36f to "M 0.16 -1 L 0.16 0 M 0.16 -1.36 L 0.165 -1.35"),
    'j' to (0.6f to "M 0.46 -1 L 0.46 0.24 C 0.46 0.62 0.06 0.62 -0.04 0.4 M 0.46 -1.36 L 0.465 -1.35"),
    'k' to (0.88f to "M 0.08 -1.5 L 0.08 0 M 0.78 -1 L 0.1 -0.42 M 0.32 -0.6 L 0.82 0"),
    'l' to (0.4f to "M 0.16 -1.5 L 0.16 -0.14 Q 0.18 0.02 0.36 -0.03"),
    'm' to (1.38f to "M 0.07 -1 L 0.07 0 M 0.07 -0.64 C 0.14 -1.06 0.68 -1.1 0.68 -0.6 L 0.68 0 M 0.68 -0.6 C 0.74 -1.06 1.28 -1.1 1.28 -0.6 L 1.28 0"),
    'n' to (0.96f to "M 0.08 -1 L 0.08 0 M 0.08 -0.6 C 0.17 -1.06 0.86 -1.1 0.86 -0.55 L 0.86 0"),
    'o' to (1.0f to "A 0.5 -0.5 0.46 0.5 -90 359"),
    'p' to (1.0f to "M 0.08 -1 L 0.08 0.5 M 0.08 -0.5 A 0.52 -0.5 0.44 0.5 180 359"),
    'q' to (1.0f to "A 0.45 -0.5 0.43 0.5 0 359 M 0.9 -1 L 0.9 0.5"),
    'r' to (0.75f to "M 0.08 -1 L 0.08 0 M 0.08 -0.55 C 0.17 -0.96 0.46 -1.06 0.72 -0.9"),
    's' to (0.92f to "M 0.8 -0.88 C 0.65 -1.06 0.1 -1.05 0.12 -0.75 C 0.14 -0.48 0.82 -0.55 0.84 -0.25 C 0.86 0.05 0.25 0.07 0.07 -0.12"),
    't' to (0.75f to "M 0.36 -1.35 L 0.36 -0.15 Q 0.38 0.02 0.66 -0.05 M 0.05 -0.98 L 0.7 -0.98"),
    'u' to (0.96f to "M 0.08 -1 L 0.08 -0.4 C 0.08 0.08 0.86 0.08 0.86 -0.4 M 0.86 -1 L 0.86 0"),
    'v' to (0.9f to "M 0.02 -1 L 0.45 0 L 0.88 -1"),
    'w' to (1.32f to "M 0.02 -1 L 0.33 0 L 0.66 -0.74 L 0.98 0 L 1.3 -1"),
    'x' to (0.86f to "M 0.06 -1 L 0.8 0 M 0.8 -1 L 0.06 0"),
    'y' to (0.9f to "M 0.04 -1 L 0.47 0 M 0.88 -1 L 0.36 0.34 C 0.27 0.56 0.1 0.56 0.0 0.48"),
    'z' to (0.92f to "M 0.08 -1 L 0.82 -1 L 0.08 0 L 0.86 0"),
    'A' to (1.12f to "M 0 0 L 0.56 -1.45 L 1.12 0 M 0.22 -0.5 L 0.9 -0.5"),
    'B' to (0.98f to "M 0.08 0 L 0.08 -1.45 C 1.0 -1.5 1.0 -0.76 0.08 -0.76 C 1.12 -0.78 1.12 0.05 0.08 0"),
    'C' to (1.25f to "A 0.68 -0.725 0.62 0.725 -40 -280"),
    'D' to (1.15f to "M 0.08 0 L 0.08 -1.45 C 1.36 -1.45 1.36 0 0.08 0"),
    'E' to (0.95f to "M 0.86 -1.45 L 0.08 -1.45 L 0.08 0 L 0.88 0 M 0.08 -0.75 L 0.7 -0.75"),
    'F' to (0.9f to "M 0.86 -1.45 L 0.08 -1.45 L 0.08 0 M 0.08 -0.75 L 0.7 -0.75"),
    'G' to (1.38f to "A 0.7 -0.725 0.62 0.725 -40 -300 L 1.3 -0.72 L 0.86 -0.72"),
    'H' to (1.1f to "M 0.08 -1.45 L 0.08 0 M 1.02 -1.45 L 1.02 0 M 0.08 -0.75 L 1.02 -0.75"),
    'I' to (0.36f to "M 0.16 -1.45 L 0.16 0"),
    'J' to (0.86f to "M 0.72 -1.45 L 0.72 -0.36 C 0.72 0.1 0.06 0.12 0.03 -0.3"),
    'K' to (1.02f to "M 0.08 -1.45 L 0.08 0 M 0.96 -1.45 L 0.1 -0.6 M 0.38 -0.86 L 1.0 0"),
    'L' to (0.9f to "M 0.08 -1.45 L 0.08 0 L 0.86 0"),
    'M' to (1.4f to "M 0.05 0 L 0.12 -1.45 L 0.7 -0.4 L 1.28 -1.45 L 1.35 0"),
    'N' to (1.14f to "M 0.08 0 L 0.08 -1.45 L 1.04 0 L 1.04 -1.45"),
    'O' to (1.45f to "A 0.72 -0.725 0.66 0.725 -90 359"),
    'P' to (0.96f to "M 0.08 0 L 0.08 -1.45 C 1.12 -1.5 1.12 -0.64 0.08 -0.68"),
    'Q' to (1.45f to "A 0.72 -0.725 0.66 0.725 -90 359 M 0.86 -0.34 L 1.4 0.1"),
    'R' to (1.02f to "M 0.08 0 L 0.08 -1.45 C 1.12 -1.5 1.12 -0.64 0.08 -0.68 M 0.44 -0.68 L 0.98 0"),
    'S' to (1.1f to "M 1.0 -1.25 C 0.85 -1.5 0.12 -1.55 0.12 -1.08 C 0.12 -0.68 1.05 -0.8 1.05 -0.35 C 1.05 0.08 0.25 0.1 0.06 -0.18"),
    'T' to (1.02f to "M 0.01 -1.45 L 1.01 -1.45 M 0.51 -1.45 L 0.51 0"),
    'U' to (1.12f to "M 0.08 -1.45 L 0.08 -0.5 C 0.08 0.16 1.04 0.16 1.04 -0.5 L 1.04 -1.45"),
    'V' to (1.1f to "M 0 -1.45 L 0.55 0 L 1.1 -1.45"),
    'W' to (1.5f to "M 0 -1.45 L 0.38 0 L 0.75 -1.05 L 1.12 0 L 1.5 -1.45"),
    'X' to (1.04f to "M 0.03 -1.45 L 1.01 0 M 1.01 -1.45 L 0.03 0"),
    'Y' to (1.04f to "M 0 -1.45 L 0.52 -0.7 L 1.04 -1.45 M 0.52 -0.7 L 0.52 0"),
    'Z' to (1.08f to "M 0.05 -1.45 L 1.02 -1.45 L 0.05 0 L 1.03 0"),
    '0' to (1.0f to "A 0.5 -0.72 0.44 0.72 -90 359"),
    '1' to (0.62f to "M 0.08 -1.2 L 0.42 -1.45 L 0.42 0"),
    '2' to (0.98f to "M 0.1 -1.15 C 0.2 -1.55 0.92 -1.55 0.9 -1.08 C 0.88 -0.7 0.3 -0.42 0.06 0 L 0.94 0"),
    '3' to (0.95f to "M 0.12 -1.3 C 0.4 -1.6 0.94 -1.45 0.86 -1.1 C 0.8 -0.85 0.5 -0.78 0.36 -0.78 C 1.08 -0.74 1.0 0.1 0.36 0.02 C 0.2 0 0.1 -0.08 0.06 -0.16"),
    '4' to (1.0f to "M 0.72 0 L 0.72 -1.45 L 0.04 -0.42 L 0.98 -0.42"),
    '5' to (0.95f to "M 0.86 -1.45 L 0.2 -1.45 L 0.13 -0.82 C 0.6 -1.02 0.98 -0.78 0.92 -0.4 C 0.86 0.08 0.25 0.1 0.06 -0.16"),
    '6' to (0.98f to "M 0.82 -1.38 C 0.42 -1.62 0.06 -1.1 0.08 -0.52 A 0.5 -0.44 0.42 0.44 180 359"),
    '7' to (0.95f to "M 0.06 -1.45 L 0.92 -1.45 L 0.34 0"),
    '8' to (0.98f to "A 0.49 -1.1 0.34 0.35 90 359 M 0.49 -0.75 A 0.49 -0.375 0.42 0.375 -90 359"),
    '9' to (0.98f to "A 0.48 -1.0 0.42 0.44 0 359 M 0.9 -1.0 C 0.9 -0.3 0.6 0.08 0.14 -0.06"),
    '.' to (0.32f to "M 0.14 -0.06 L 0.145 -0.05"),
    ',' to (0.32f to "M 0.18 -0.06 L 0.08 0.22"),
    '!' to (0.34f to "M 0.16 -1.45 L 0.16 -0.42 M 0.16 -0.06 L 0.165 -0.05"),
    '?' to (0.85f to "M 0.08 -1.18 C 0.2 -1.6 0.86 -1.56 0.82 -1.1 C 0.8 -0.78 0.4 -0.76 0.4 -0.38 M 0.4 -0.06 L 0.405 -0.05"),
    '\'' to (0.3f to "M 0.15 -1.45 L 0.13 -1.08"),
    '-' to (0.62f to "M 0.08 -0.5 L 0.54 -0.5"),
    '&' to (1.1f to "M 1.05 0 L 0.28 -0.9 C 0.1 -1.16 0.24 -1.45 0.48 -1.45 C 0.72 -1.45 0.82 -1.16 0.6 -0.96 L 0.2 -0.62 C -0.06 -0.36 0.1 0.04 0.44 0.02 C 0.7 0 0.86 -0.2 0.96 -0.5"),
    '♥' to (1.1f to "M 0.55 0 C 0.0 -0.4 0.0 -0.95 0.3 -1.0 C 0.45 -1.02 0.55 -0.9 0.55 -0.78 C 0.55 -0.9 0.65 -1.02 0.8 -1.0 C 1.1 -0.95 1.1 -0.4 0.55 0"),
)

private const val SPACE = 0.55f
private const val TRACK = 0.26f
private const val SLANT = 0.16f
internal const val ICING_MAX_LETTERS = 14

/** A sampled stroke: points (x, y flat, pt) and running length at each point. */
internal class IcingStroke(val pts: FloatArray, val len: FloatArray, val glyph: Int) {
    val full get() = len[len.size - 1]
}

internal class IcingLettering(
    val strokes: List<IcingStroke>,
    /** Per glyph: its first stroke, stroke count, and ink length. */
    val firstStroke: IntArray,
    val strokeCount: IntArray,
    val glyphLength: FloatArray,
    /** Glyph origins (pt), used to glide letters when the layout changes. */
    val originX: FloatArray,
    val originY: FloatArray,
    val total: Float,
    val size: Float,
)

private fun hash(n: Int): Double {
    val s = sin(n * 12.9898) * 43758.5453
    return s - floor(s)
}

/** Samples a glyph into strokes of points in font units. */
private fun sampleGlyph(src: String): List<FloatArray> {
    val t = src.trim().split(Regex("\\s+"))
    val strokes = ArrayList<FloatArray>()
    var cur = ArrayList<Float>()
    var x = 0f
    var y = 0f
    fun push(px: Float, py: Float) { cur.add(px); cur.add(py); x = px; y = py }
    var i = 0
    fun num(): Float = t[i++].toFloat()
    while (i < t.size) {
        when (t[i++]) {
            "M" -> {
                if (cur.size > 2) strokes.add(cur.toFloatArray())
                cur = ArrayList()
                val a = num(); val b = num()
                push(a, b)
            }
            "L" -> {
                val nx = num(); val ny = num()
                val steps = max(1, ceil(hypot(nx - x, ny - y) / 0.06f).toInt())
                val x0 = x; val y0 = y
                for (k in 1..steps) push(x0 + (nx - x0) * k / steps, y0 + (ny - y0) * k / steps)
            }
            "Q" -> {
                val cx = num(); val cy = num(); val nx = num(); val ny = num()
                val x0 = x; val y0 = y
                for (k in 1..16) {
                    val u = k / 16f
                    push((1 - u) * (1 - u) * x0 + 2 * u * (1 - u) * cx + u * u * nx, (1 - u) * (1 - u) * y0 + 2 * u * (1 - u) * cy + u * u * ny)
                }
            }
            "C" -> {
                val c1x = num(); val c1y = num(); val c2x = num(); val c2y = num(); val nx = num(); val ny = num()
                val x0 = x; val y0 = y
                for (k in 1..24) {
                    val u = k / 24f
                    val v = 1 - u
                    push(
                        v * v * v * x0 + 3 * v * v * u * c1x + 3 * v * u * u * c2x + u * u * u * nx,
                        v * v * v * y0 + 3 * v * v * u * c1y + 3 * v * u * u * c2y + u * u * u * ny,
                    )
                }
            }
            "A" -> {
                val cx = num(); val cy = num(); val rx = num(); val ry = num()
                val a0 = num() * PI.toFloat() / 180
                val sw = num() * PI.toFloat() / 180
                val steps = ceil(abs(sw) / 0.12f).toInt()
                // An arc after a line continues from where the line ended.
                push(cx + rx * cos(a0), cy + ry * sin(a0))
                for (k in 1..steps) {
                    val a = a0 + sw * k / steps
                    push(cx + rx * cos(a), cy + ry * sin(a))
                }
            }
        }
    }
    if (cur.size > 2) strokes.add(cur.toFloatArray())
    return strokes
}

private val cache = HashMap<String, Pair<Float, List<FloatArray>>>()

private fun glyph(ch: String): Pair<Float, List<FloatArray>>? {
    cache[ch]?.let { return it }
    if (ch.isEmpty()) return null
    val key = if (ch.length == 1) ch[0] else return null
    val def = G[key] ?: G[key.lowercaseChar()] ?: return null
    return (def.first to sampleGlyph(def.second)).also { cache[ch] = it }
}

private fun lineWidth(chars: List<String>): Float {
    var w = 0f
    chars.forEachIndexed { i, ch ->
        w += if (ch == " ") SPACE else (glyph(ch)?.first ?: SPACE) + if (i < chars.size - 1) TRACK else 0f
    }
    return w
}

/** The characters of a string as the user sees them (code points). */
internal fun icingChars(text: String): List<String> {
    val out = ArrayList<String>()
    var i = 0
    while (i < text.length) {
        val n = Character.charCount(text.codePointAt(i))
        out.add(text.substring(i, i + n))
        i += n
    }
    return out
}

/**
 * Lays the message out on the cake: one line, or two split at the space nearest the middle, fitted
 * into a box boxW × boxH pt centred on the cake (cake-local coordinates).
 */
internal fun icingLayout(text: String, boxW: Float, boxH: Float): IcingLettering {
    val chars = icingChars(text).take(ICING_MAX_LETTERS)
    var lines = listOf(chars)
    val spaces = chars.indices.filter { chars[it] == " " && it > 0 }
    if (chars.size > 7 && spaces.isNotEmpty()) {
        var best = spaces[0]
        for (s in spaces) if (abs(s - chars.size / 2f) < abs(best - chars.size / 2f)) best = s
        lines = listOf(chars.subList(0, best), chars.subList(best + 1, chars.size))
    }
    val widest = max(0.01f, lines.maxOf { lineWidth(it) })
    // x-height in pt: as large as the box allows, capped so short names don't get huge.
    val rows = lines.size
    val size = min(min(boxW / (widest + SLANT * 1.5f), boxH / (rows * 1.5f + (rows - 1) * 0.85f + 0.4f)), 56f)
    val strokes = ArrayList<IcingStroke>()
    val firstStroke = ArrayList<Int>(); val strokeCount = ArrayList<Int>(); val glyphLength = ArrayList<Float>()
    val originX = ArrayList<Float>(); val originY = ArrayList<Float>()
    var total = 0f
    val lineGap = 2.3f * size
    val blockH = (rows - 1) * lineGap
    var gi = 0
    var seedBase = 0
    lines.forEachIndexed { li, line ->
        val w = lineWidth(line) * size
        var x = -w / 2
        // Baselines sit so the x-height of the block is centred.
        val base = -blockH / 2 + li * lineGap + size * 0.5f
        for (ch in line) {
            val g = if (ch == " ") null else glyph(ch)
            firstStroke.add(strokes.size)
            originX.add(x)
            originY.add(base)
            var ink = 0f
            if (g != null) {
                for (src in g.second) {
                    val pts = FloatArray(src.size)
                    val len = FloatArray(src.size / 2)
                    var run = 0f
                    var k = 0
                    while (k < src.size) {
                        val fx = src[k]; val fy = src[k + 1]
                        // Slant like hand lettering, then a slow seeded wobble across the stroke.
                        val px = x + (fx - fy * SLANT) * size
                        val py = base + fy * size
                        val wob = ((sin(k * 0.21 + seedBase * 1.7) + 0.6 * sin(k * 0.53 + hash(seedBase) * 6)) * size * 0.012).toFloat()
                        val qx = px + wob
                        val qy = py - wob * 0.6f
                        if (k >= 2) run += hypot(qx - pts[k - 2], qy - pts[k - 1])
                        pts[k] = qx; pts[k + 1] = qy
                        len[k / 2] = run
                        k += 2
                    }
                    seedBase += 1
                    strokes.add(IcingStroke(pts, len, gi))
                    ink += max(run, 0.5f)
                }
                x += (g.first + TRACK) * size
            } else x += (if (ch == " ") SPACE else SPACE + TRACK) * size
            strokeCount.add(strokes.size - firstStroke[gi])
            glyphLength.add(ink)
            total += ink
            gi += 1
        }
    }
    return IcingLettering(
        strokes, firstStroke.toIntArray(), strokeCount.toIntArray(), glyphLength.toFloatArray(),
        originX.toFloatArray(), originY.toFloatArray(), total, size,
    )
}

/** A piping schedule for scripted lettering: per stroke the fraction of the window at which it starts and ends. */
internal fun icingSchedule(l: IcingLettering): Pair<FloatArray, FloatArray> {
    val n = l.strokes.size
    val ink = FloatArray(n) { l.strokes[it].full }
    val travel = FloatArray(n) { i ->
        if (i == 0) 0f
        else {
            val a = l.strokes[i - 1].pts
            val b = l.strokes[i].pts
            0.35f * hypot(b[0] - a[a.size - 2], b[1] - a[a.size - 1]) + 6
        }
    }
    var total = 0f
    for (i in 0 until n) total += ink[i] + travel[i]
    val t0 = FloatArray(n); val t1 = FloatArray(n)
    var acc = 0f
    for (i in 0 until n) {
        acc += travel[i]
        t0[i] = acc / total
        acc += ink[i]
        t1[i] = acc / total
    }
    return t0 to t1
}
