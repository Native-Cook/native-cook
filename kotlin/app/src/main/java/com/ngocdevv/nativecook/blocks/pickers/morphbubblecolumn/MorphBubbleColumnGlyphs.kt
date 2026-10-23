package com.ngocdevv.nativecook.blocks.pickers.morphbubblecolumn

import android.graphics.Path

// A small dot-matrix alphabet, seven pixels high, for the clock and the reading of an open site. Each row is a
// string of '1' (lit) and '0' (dark); a glyph may be narrower than five pixels.
internal val PIXEL_GLYPHS = mapOf(
    '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    '3' to listOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
    '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
    '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
    '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    '+' to listOf("00000", "00100", "00100", "11111", "00100", "00100", "00000"),
    '-' to listOf("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
    ':' to listOf("0", "1", "1", "0", "1", "1", "0"),
    'P' to listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    'M' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    ' ' to listOf("000", "000", "000", "000", "000", "000", "000"),
)

/** A pixel globe, seven by seven, like the one beside the clock in the reference. */
internal val PIXEL_GLOBE = listOf("0011100", "0101010", "1111111", "1010101", "1111111", "0101010", "0011100")

/** The glyph rows for each character of `text`, for [pixelBlockPath]. */
internal fun pixelGlyphs(text: String): List<List<String>> = text.map { PIXEL_GLYPHS[it] ?: PIXEL_GLYPHS.getValue(' ') }

/** The lit pixels of `rows` as filled squares, `px` wide with `gap` between them, its top left at (x, y). */
internal fun pixelBlockPath(rows: List<List<String>>, x: Float, y: Float, px: Float, gap: Float): Path {
    val path = Path()
    var left = x
    for (glyph in rows) {
        for ((r, row) in glyph.withIndex()) {
            for ((c, bit) in row.withIndex()) {
                if (bit != '1') continue
                val l = left + c * px + gap / 2f
                val t = y + r * px + gap / 2f
                path.addRect(l, t, l + px - gap, t + px - gap, Path.Direction.CW)
            }
        }
        left += (glyph[0].length + 1) * px
    }
    return path
}
