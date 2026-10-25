package com.ngocdevv.nativecook.blocks.inputs.crossstitchinput

import kotlin.math.abs
import kotlin.math.max

/*
 A 5 × 7 sampler alphabet, the kind printed on cross-stitch charts, and the layout of a name on the
 aida grid (as in React Native). Capitals and digits use rows 0–6; lower case uses the same rows with
 ascenders in rows 0–1 and descenders in rows 7–8. 'X' is a stitch.
*/

private val GLYPHS = mapOf(
    'A' to ".XXX.|X...X|X...X|XXXXX|X...X|X...X|X...X",
    'B' to "XXXX.|X...X|X...X|XXXX.|X...X|X...X|XXXX.",
    'C' to ".XXX.|X...X|X....|X....|X....|X...X|.XXX.",
    'D' to "XXXX.|X...X|X...X|X...X|X...X|X...X|XXXX.",
    'E' to "XXXXX|X....|X....|XXXX.|X....|X....|XXXXX",
    'F' to "XXXXX|X....|X....|XXXX.|X....|X....|X....",
    'G' to ".XXX.|X...X|X....|X.XXX|X...X|X...X|.XXXX",
    'H' to "X...X|X...X|X...X|XXXXX|X...X|X...X|X...X",
    'I' to "XXX|.X.|.X.|.X.|.X.|.X.|XXX",
    'J' to "..XXX|...X.|...X.|...X.|...X.|X..X.|.XX..",
    'K' to "X...X|X..X.|X.X..|XX...|X.X..|X..X.|X...X",
    'L' to "X....|X....|X....|X....|X....|X....|XXXXX",
    'M' to "X...X|XX.XX|X.X.X|X.X.X|X...X|X...X|X...X",
    'N' to "X...X|XX..X|X.X.X|X..XX|X...X|X...X|X...X",
    'O' to ".XXX.|X...X|X...X|X...X|X...X|X...X|.XXX.",
    'P' to "XXXX.|X...X|X...X|XXXX.|X....|X....|X....",
    'Q' to ".XXX.|X...X|X...X|X...X|X.X.X|X..X.|.XX.X",
    'R' to "XXXX.|X...X|X...X|XXXX.|X.X..|X..X.|X...X",
    'S' to ".XXXX|X....|X....|.XXX.|....X|....X|XXXX.",
    'T' to "XXXXX|..X..|..X..|..X..|..X..|..X..|..X..",
    'U' to "X...X|X...X|X...X|X...X|X...X|X...X|.XXX.",
    'V' to "X...X|X...X|X...X|X...X|X...X|.X.X.|..X..",
    'W' to "X...X|X...X|X...X|X.X.X|X.X.X|X.X.X|.X.X.",
    'X' to "X...X|X...X|.X.X.|..X..|.X.X.|X...X|X...X",
    'Y' to "X...X|X...X|.X.X.|..X..|..X..|..X..|..X..",
    'Z' to "XXXXX|....X|...X.|..X..|.X...|X....|XXXXX",
    'a' to ".....|.....|.XXX.|....X|.XXXX|X...X|.XXXX",
    'b' to "X....|X....|XXXX.|X...X|X...X|X...X|XXXX.",
    'c' to ".....|.....|.XXX.|X....|X....|X...X|.XXX.",
    'd' to "....X|....X|.XXXX|X...X|X...X|X...X|.XXXX",
    'e' to ".....|.....|.XXX.|X...X|XXXXX|X....|.XXX.",
    'f' to "..XX.|.X..X|.X...|XXX..|.X...|.X...|.X...",
    'g' to ".....|.....|.XXXX|X...X|X...X|X...X|.XXXX|....X|.XXX.",
    'h' to "X....|X....|XXXX.|X...X|X...X|X...X|X...X",
    'i' to ".X.|...|XX.|.X.|.X.|.X.|XXX",
    'j' to "...X|....|..XX|...X|...X|...X|...X|X..X|.XX.",
    'k' to "X...|X...|X..X|X.X.|XX..|X.X.|X..X",
    'l' to "XX.|.X.|.X.|.X.|.X.|.X.|XXX",
    'm' to ".....|.....|XX.X.|X.X.X|X.X.X|X.X.X|X.X.X",
    'n' to ".....|.....|XXXX.|X...X|X...X|X...X|X...X",
    'o' to ".....|.....|.XXX.|X...X|X...X|X...X|.XXX.",
    'p' to ".....|.....|XXXX.|X...X|X...X|X...X|XXXX.|X....|X....",
    'q' to ".....|.....|.XXXX|X...X|X...X|X...X|.XXXX|....X|....X",
    'r' to ".....|.....|X.XX.|XX..X|X....|X....|X....",
    's' to ".....|.....|.XXXX|X....|.XXX.|....X|XXXX.",
    't' to ".X...|.X...|XXX..|.X...|.X...|.X..X|..XX.",
    'u' to ".....|.....|X...X|X...X|X...X|X..XX|.XX.X",
    'v' to ".....|.....|X...X|X...X|X...X|.X.X.|..X..",
    'w' to ".....|.....|X...X|X...X|X.X.X|X.X.X|.X.X.",
    'x' to ".....|.....|X...X|.X.X.|..X..|.X.X.|X...X",
    'y' to ".....|.....|X...X|X...X|X...X|X...X|.XXXX|....X|.XXX.",
    'z' to ".....|.....|XXXXX|...X.|..X..|.X...|XXXXX",
    '0' to ".XXX.|X...X|X..XX|X.X.X|XX..X|X...X|.XXX.",
    '1' to "..X..|.XX..|..X..|..X..|..X..|..X..|.XXX.",
    '2' to ".XXX.|X...X|....X|...X.|..X..|.X...|XXXXX",
    '3' to "XXXX.|....X|....X|.XXX.|....X|....X|XXXX.",
    '4' to "...X.|..XX.|.X.X.|X..X.|XXXXX|...X.|...X.",
    '5' to "XXXXX|X....|XXXX.|....X|....X|X...X|.XXX.",
    '6' to ".XXX.|X....|X....|XXXX.|X...X|X...X|.XXX.",
    '7' to "XXXXX|....X|...X.|..X..|.X...|.X...|.X...",
    '8' to ".XXX.|X...X|X...X|.XXX.|X...X|X...X|.XXX.",
    '9' to ".XXX.|X...X|X...X|.XXXX|....X|....X|.XXX.",
    '.' to "X|X",
    '!' to "X|X|X|X|X|.|X",
    '-' to "...|...|...|XXX|...|...|...",
    '\'' to "X|X",
    '&' to ".XX..|X..X.|.XX..|.X...|X.X.X|X..X.|.XX.X",
)

private class StitchGlyph(val rows: List<BooleanArray>, val width: Int, val top: Int)

private val cache = HashMap<String, StitchGlyph>()

private fun glyph(ch: String): StitchGlyph? {
    if (ch == " ") return StitchGlyph(emptyList(), 3, 0)
    cache[ch]?.let { return it }
    if (ch.length != 1) return null
    val src = GLYPHS[ch[0]] ?: return null
    val raw = src.split('|').map { r -> BooleanArray(r.length) { r[it] == 'X' } }
    // '.' sits low.
    val top = if (ch == ".") 5 else 0
    val width = raw.maxOf { it.size }
    val rows = raw.map { r -> BooleanArray(width) { it < r.size && r[it] } }
    return StitchGlyph(rows, width, top).also { cache[ch] = it }
}

internal const val STITCH_LINE_COLS = 24
internal const val MAX_LETTERS = 10

/** The characters of a string as the user sees them (code points). */
internal fun stitchChars(text: String): List<String> {
    val out = ArrayList<String>()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val n = Character.charCount(cp)
        out.add(text.substring(i, i + n))
        i += n
    }
    return out
}

/**
 * A laid-out name: the cell of every stitch (grid units, the fabric's centre at 0), the glyph each
 * stitch belongs to, and per glyph its first stitch, count and top-left cell. Stitches are listed in
 * working order: glyph by glyph, rows top to bottom, each row worked back from where the last ended.
 */
internal class StitchLayout(
    val cx: FloatArray, val cy: FloatArray, val glyphOf: IntArray,
    val first: IntArray, val count: IntArray, val col: FloatArray, val row: FloatArray,
) {
    val total = cx.size
}

internal fun stitchLayoutName(text: String): StitchLayout {
    val chars = stitchChars(text).take(MAX_LETTERS)
    val glyphs = chars.map { glyph(it) ?: glyph(it.uppercase()) }
    fun advance(from: Int, to: Int): Int {
        var w = 0
        for (i in from until to) w += (glyphs[i]?.width ?: 0) + if (i < to - 1) 1 else 0
        return w
    }
    var lines = listOf(0 to chars.size)
    if (advance(0, chars.size) > STITCH_LINE_COLS && chars.size > 1) {
        var best = 1
        var bestScore = Int.MAX_VALUE
        for (s in 1 until chars.size) {
            val a = advance(0, s)
            val b = advance(if (chars[s] == " ") s + 1 else s, chars.size)
            val score = max(a, b) - if (chars[s] == " ") 3 else 0
            if (score < bestScore) { bestScore = score; best = s }
        }
        lines = listOf(0 to best, (if (chars[best] == " ") best + 1 else best) to chars.size)
    }
    val cx = ArrayList<Float>(); val cy = ArrayList<Float>(); val glyphOf = ArrayList<Int>()
    val n = chars.size
    val first = IntArray(n); val count = IntArray(n); val col = FloatArray(n); val row = FloatArray(n)
    // One line: capitals on rows -3..3 (centred). Two lines: capitals on rows -8..-2 and 1..7.
    val tops = if (lines.size == 1) intArrayOf(-3) else intArrayOf(-8, 1)
    lines.forEachIndexed { li, (from, to) ->
        val w = advance(from, to)
        var c0 = -Math.floorDiv(w, 2)
        val rowTop = tops[li]
        for (i in from until to) {
            val g = glyphs[i]
            first[i] = cx.size
            col[i] = c0.toFloat()
            row[i] = rowTop.toFloat()
            if (g != null) {
                var leftToRight = true
                for (r in g.rows.indices) {
                    val cols = ArrayList<Int>()
                    for (c in 0 until g.width) if (g.rows[r][c]) cols.add(c)
                    if (cols.isEmpty()) continue
                    if (!leftToRight) cols.reverse()
                    for (c in cols) {
                        cx.add((c0 + c).toFloat())
                        cy.add((rowTop + g.top + r).toFloat())
                        glyphOf.add(i)
                    }
                    leftToRight = !leftToRight
                }
                c0 += g.width + 1
            }
            count[i] = cx.size - first[i]
        }
    }
    return StitchLayout(cx.toFloatArray(), cy.toFloatArray(), glyphOf.toIntArray(), first, count, col, row)
}

/** The sampler's printed border: a row of little hearts above and a zig-zag below (cell units). */
internal fun stitchBorder(): Pair<FloatArray, FloatArray> {
    val cx = ArrayList<Float>(); val cy = ArrayList<Float>()
    val heart = listOf(".X.X.", "XXXXX", ".XXX.", "..X..")
    for (k in -1..1) {
        val ox = k * 7 - 2
        heart.forEachIndexed { r, line ->
            for (c in line.indices) if (line[c] == 'X') { cx.add((ox + c).toFloat()); cy.add((-12 + r).toFloat()) }
        }
    }
    for (c in -9..9) { cx.add(c.toFloat()); cy.add((10 + if (abs(c) % 2 == 0) 0 else 1).toFloat()) }
    return cx.toFloatArray() to cy.toFloatArray()
}
