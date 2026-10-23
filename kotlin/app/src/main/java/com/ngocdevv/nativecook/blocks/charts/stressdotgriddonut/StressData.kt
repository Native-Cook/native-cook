package com.ngocdevv.nativecook.blocks.charts.stressdotgriddonut

import androidx.compose.ui.graphics.Color

/**
 * One view of the heat map: rows by columns of stress levels, with what the screen says about it.
 *
 * @param label The tab's label.
 * @param levels One string per row, one character per column: '.' no data, 'L' low, 'N' normal, 'H' high.
 * @param now The cell of now, ringed: row to column.
 * @param unit What a counted cell is called, after its count.
 */
data class Period(
    val label: String,
    val range: String,
    val rows: List<String>,
    val columns: List<String>,
    val levels: List<String>,
    val now: Pair<Int, Int>,
    val unit: String,
    val busiestTitle: String,
    val busiest: String,
)

internal object LevelColors {
    val Low = Color(0xFFD4F53C)
    val Normal = Color(0xFF8E8CF7)
    val High = Color(0xFFFF5A3C)
    val None = Color(0xFF3A3A40)

    /** By level: no data, low, normal, high. */
    val ByLevel = listOf(None, Low, Normal, High)
}

/** A day by two-hour periods: seven rows of twelve. */
val Days = Period(
    label = "7 Days",
    range = "Oct 5, 2026 - Oct 11, 2026",
    rows = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    columns = listOf("00", "02", "04", "06", "08", "10", "12", "14", "16", "18", "20", "22"),
    levels = listOf(
        "HHL..NNN...N",
        "NHNH.HLLH.N.",
        "LLNHHLHNN...",
        "HNHLHLNL...N",
        "HHLNNLNLNH..",
        "LNL......HLL",
        "HNL.NH......",
    ),
    now = 6 to 10,
    unit = "periods",
    busiestTitle = "Most stressful times",
    busiest = "0:00 and 2:00",
)

/** Four weeks by day: four rows of seven. */
val Weeks = Period(
    label = "4 Weeks",
    range = "Sep 14, 2026 - Oct 11, 2026",
    rows = listOf("09/14 - 09/20", "09/21 - 09/27", "09/28 - 10/04", "10/05 - 10/11"),
    columns = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    levels = listOf("NNNLNNN", "HNNNNLN", "NNHNHHN", "NNNNNLN"),
    now = 3 to 6,
    unit = "days",
    busiestTitle = "Most stressful days",
    busiest = "Monday and Friday",
)

/** 0: no data, 1: low, 2: normal, 3: high. */
internal fun Period.levelAt(row: Int, column: Int): Int = when (levels.getOrNull(row)?.getOrNull(column)) {
    'L' -> 1
    'N' -> 2
    'H' -> 3
    else -> 0
}

/** How many cells are low, normal and high. */
internal fun Period.counts(): List<Int> {
    val tally = IntArray(3)
    for (r in rows.indices) {
        for (c in columns.indices) {
            val level = levelAt(r, c)
            if (level > 0) tally[level - 1] += 1
        }
    }
    return tally.toList()
}
