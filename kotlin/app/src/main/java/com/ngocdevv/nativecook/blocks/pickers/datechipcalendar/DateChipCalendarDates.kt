package com.ngocdevv.nativecook.blocks.pickers.datechipcalendar

import androidx.compose.ui.graphics.Path
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Plain date math on day serials (days since 1970-01-01), and the range's one continuous shape (RN calendar.ts, rangePath.ts).

class CalendarMonth(
    val key: String,
    val title: String,
    val short: String,
    /** Serial of the 1st. */
    val first: Int,
    /** Weekday of the 1st, Sunday = 0. */
    val offset: Int,
    val days: Int,
)

private val NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

internal val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")

/** `month` is 0-based, as in JavaScript. */
fun calendarSerial(year: Int, month: Int, day: Int): Int = LocalDate.of(year, month + 1, day).toEpochDay().toInt()

fun makeCalendarMonths(year: Int, month: Int, count: Int): List<CalendarMonth> = List(count) { i ->
    val date = LocalDate.of(year, month + 1, 1).plusMonths(i.toLong())
    val m = date.monthValue - 1
    CalendarMonth(
        key = "${date.year}-$m",
        title = "${NAMES[m]} ${date.year}",
        short = NAMES[m].take(3),
        first = date.toEpochDay().toInt(),
        offset = date.dayOfWeek.value % 7,
        days = date.lengthOfMonth(),
    )
}

internal fun formatDay(value: Int): String {
    val date = LocalDate.ofEpochDay(value.toLong())
    return "${NAMES[date.monthValue - 1].take(3)} ${date.dayOfMonth}"
}

internal fun formatRange(start: Int, end: Int) = "${formatDay(start)} – ${formatDay(end)}"

/** Corners rounded with a quadratic through each vertex, radius capped at half of either edge. */
private fun addRoundedPolygon(out: Path, xs: FloatArray, ys: FloatArray, n: Int, radius: Float) {
    for (i in 0 until n) {
        val px = xs[i]
        val py = ys[i]
        val prevX = xs[(i + n - 1) % n]
        val prevY = ys[(i + n - 1) % n]
        val nextX = xs[(i + 1) % n]
        val nextY = ys[(i + 1) % n]
        val inX = px - prevX
        val inY = py - prevY
        val outX = nextX - px
        val outY = nextY - py
        val inLen = hypot(inX, inY).let { if (it == 0f) 1f else it }
        val outLen = hypot(outX, outY).let { if (it == 0f) 1f else it }
        val r = min(radius, min(inLen / 2f, outLen / 2f))
        val ax = px - inX / inLen * r
        val ay = py - inY / inLen * r
        val cx = px + outX / outLen * r
        val cy = py + outY / outLen * r
        if (i == 0) out.moveTo(ax, ay) else out.lineTo(ax, ay)
        out.quadraticTo(px, py, cx, cy)
    }
    out.close()
}

private fun addPill(out: Path, x0: Float, x1: Float, top: Float, bottom: Float, radius: Float) {
    addRoundedPolygon(out, floatArrayOf(x0, x1, x1, x0), floatArrayOf(top, top, bottom, bottom), 4, radius)
}

/**
 * The range from day `start` to the (fractional) day `head`, clipped to one month, into `out` (reset first).
 * `first`, `offset`, `days` describe the month; `cell` and `row` are the grid's pitch; `inset` trims each row's band.
 */
internal fun buildRange(
    out: Path,
    start: Float,
    head: Float,
    first: Int,
    offset: Int,
    days: Int,
    cell: Float,
    row: Float,
    inset: Float,
) {
    out.reset()
    if (start < 0f) return
    val lo = min(start, head)
    val hi = max(start, head)
    val last = first + days - 1
    if (hi < first || lo > last) return

    val from = max(lo, first.toFloat()) - first + offset
    val to = min(hi, last.toFloat()) - first + offset + 1 // exclusive end, fractional while the head travels
    val rowA = floor(from / 7f)
    val rowB = floor((to - 0.0001f) / 7f)
    val xA = (from - rowA * 7f) * cell
    val xB = (to - rowB * 7f) * cell
    val width = cell * 7f
    val radius = (row - inset * 2f) / 2f
    val topA = rowA * row + inset
    val bottomA = (rowA + 1f) * row - inset
    val topB = rowB * row + inset
    val bottomB = (rowB + 1f) * row - inset

    if (rowA == rowB) {
        addPill(out, xA, xB, topA, bottomA, radius)
        return
    }
    // Two weeks that do not overlap cannot be joined: two pills.
    if (rowB == rowA + 1f && xB <= xA + radius) {
        addPill(out, xA, width, topA, bottomA, radius)
        addPill(out, 0f, xB, topB, bottomB, radius)
        return
    }
    val rawX = floatArrayOf(xA, width, width, xB, xB, 0f, 0f, xA)
    val rawY = floatArrayOf(topA, topA, topB - inset * 2f, topB - inset * 2f, bottomB, bottomB, bottomA + inset * 2f, bottomA + inset * 2f)
    // Drop repeated points and points on a straight line, which would round into nothing.
    val xs = FloatArray(8)
    val ys = FloatArray(8)
    var n = 0
    for (i in 0 until 8) {
        val px = rawX[i]
        val py = rawY[i]
        val prevX = rawX[(i + 7) % 8]
        val prevY = rawY[(i + 7) % 8]
        val nextX = rawX[(i + 1) % 8]
        val nextY = rawY[(i + 1) % 8]
        val same = abs(px - prevX) < 0.01f && abs(py - prevY) < 0.01f
        val straight = (abs(prevX - px) < 0.01f && abs(px - nextX) < 0.01f) || (abs(prevY - py) < 0.01f && abs(py - nextY) < 0.01f)
        if (!same && !straight) {
            xs[n] = px
            ys[n] = py
            n += 1
        }
    }
    if (n >= 3) addRoundedPolygon(out, xs, ys, n, radius)
}
