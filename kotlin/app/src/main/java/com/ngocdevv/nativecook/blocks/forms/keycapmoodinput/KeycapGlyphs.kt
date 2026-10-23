package com.ngocdevv.nativecook.blocks.forms.keycapmoodinput

import kotlin.math.hypot

internal data class KeycapDot(val x: Double, val y: Double)
private val wordBitmaps = mapOf(
    'i' to arrayOf(".X.", "...", "XX.", ".X.", ".X.", "XXX"),
    'f' to arrayOf("..XX", ".X..", "XXXX", ".X..", ".X..", ".X.."),
    'e' to arrayOf("....", "....", ".XX.", "XXXX", "X...", ".XXX"),
    'l' to arrayOf("XX.", ".X.", ".X.", ".X.", ".X.", "XXX"),
    's' to arrayOf("....", "....", ".XXX", "XX..", "..XX", "XXX."),
    'o' to arrayOf("....", "....", ".XX.", "X..X", "X..X", ".XX."),
    'w' to arrayOf(".....", ".....", "X...X", "X.X.X", "X.X.X", ".X.X."),
    't' to arrayOf("....", ".X..", "XXXX", ".X..", ".X..", "..XX"),
    'v' to arrayOf(".....", ".....", "X...X", "X...X", ".X.X.", "..X.."),
    'r' to arrayOf("....", "....", "X.XX", "XX..", "X...", "X..."),
    'y' to arrayOf("....", "....", "X..X", "X..X", "X..X", ".XXX", "...X", ".XX."),
    'a' to arrayOf("....", "....", ".XXX", "X..X", "X..X", ".XXX"),
    'b' to arrayOf("X...", "X...", "XXX.", "X..X", "X..X", "XXX."),
    'u' to arrayOf("....", "....", "X..X", "X..X", "X..X", ".XXX"),
)
internal val keycapFaceBitmaps = arrayOf(
    arrayOf(".XXX....XXX.", "X...X..X...X", "X...X..X...X", "............", "............", "X..........X", "X..........X", ".X........X.", "..X......X..", "...XXXXXX..."),
    arrayOf(".X.X....X.X.", "XXXXX..XXXXX", "XXXXX..XXXXX", ".XXX....XXX.", "..X......X..", "............", "X..........X", ".X........X.", "..X......X..", "...XXXXXX..."),
    arrayOf(".XXX....XXX.", ".X.X....X.X.", ".XXX....XXX.", "............", "............", "....XXXX....", "...X....X...", "...X....X...", "...X....X...", "....XXXX...."),
    arrayOf("............", "X...X..X...X", ".XXX....XXX.", "............", "............", "............", "............", "..X......X..", "...XXXXXX...", "............"),
    arrayOf("...X....X...", "..X......X..", "............", ".XX......XX.", ".XX......XX.", "............", "............", "...XXXXXX...", "..X......X..", ".X........X."),
    arrayOf("X..........X", ".XX......XX.", "...X....X...", ".XX......XX.", ".XX......XX.", "............", "............", "............", "..XXXXXXXX..", "............"),
)
private fun wordDots(word: String): List<KeycapDot> {
    var width = 0
    for (i in word.indices) {
        width += if (word[i] == ' ') 3 else wordBitmaps.getValue(word[i])[0].length
        if (word[i] != ' ' && i < word.lastIndex && word[i + 1] != ' ') width++
    }
    val dots = ArrayList<KeycapDot>()
    var cursor = 0
    for (i in word.indices) {
        if (word[i] == ' ') { cursor += 3; continue }
        val rows = wordBitmaps.getValue(word[i])
        for (y in rows.indices) for (x in rows[y].indices) {
            if (rows[y][x] == 'X') dots.add(KeycapDot(cursor + x - (width - 1) / 2.0, y - 3.5))
        }
        cursor += rows[0].length + if (i < word.lastIndex && word[i + 1] != ' ') 1 else 0
    }
    return dots
}
private fun faceDots(rows: Array<String>): List<KeycapDot> = buildList {
    for (y in 0..9) for (x in 0..11) if (rows[y][x] == 'X') add(KeycapDot(x - 5.5, y - 4.5))
}
internal val keycapLegends = arrayOf(
    keycapWords[0].map(::wordDots),
    keycapWords[1].map(::wordDots),
    keycapWords[2].map(::wordDots),
    keycapFaceBitmaps.map(::faceDots),
)
internal fun keycapDotPitch(key: Int, u: Double, ratio: Double): Double =
    kotlin.math.round((if (key == 3) 16.0 / 3 else 13.0 / 3) * u * ratio) / ratio

/** 0: stays, 1: disappears, 2: appears. Delays use the actual touch origin. */
internal fun keycapFlipDots(key: Int, flip: KeycapFlip, pitch: Double, ox: Double, oy: Double): List<Pair<KeycapDot, Double>> {
    if (flip.t < 0 || flip.mode == 2) return keycapLegends[key][flip.to].map { it to 1.0 }
    val before = keycapLegends[key][flip.from]
    val after = keycapLegends[key][flip.to]
    val result = ArrayList<Pair<KeycapDot, Double>>()
    for (dot in before) {
        if (after.contains(dot)) { result.add(dot to 1.0); continue }
        val delay = if (key == 3) hypot(dot.x * pitch - ox, dot.y * pitch - oy) * 0.9 else (dot.y + 3.5) * 14
        val t = ((flip.t - delay) / 110).coerceIn(0.0, 1.0)
        if (t < 0.5) result.add(dot to kotlin.math.abs(kotlin.math.cos(Math.PI * t)))
    }
    for (dot in after) {
        if (before.contains(dot)) continue
        val delay = if (key == 3) hypot(dot.x * pitch - ox, dot.y * pitch - oy) * 0.9 else (dot.y + 3.5) * 14
        val t = ((flip.t - delay) / 110).coerceIn(0.0, 1.0)
        if (t >= 0.5) result.add(dot to kotlin.math.abs(kotlin.math.cos(Math.PI * t)))
    }
    return result
}
