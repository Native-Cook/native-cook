package com.ngocdevv.nativecook.blocks.charts.stackedbarsglasstooltip

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** One column: its label under the axis, the tooltip's title, and grams of each macro. */
data class StackedBarsGlassTooltipDay(val label: String, val title: String, val carbs: Int, val fat: Int, val protein: Int)

class StackedBarsGlassTooltipRange(
    val key: String,
    /** The segmented control's label. */
    val name: String,
    /** The period, under the screen's title. */
    val period: String,
    /** Calories at the top of the axis. */
    val max: Double,
    /** The change against the period before, in percent. */
    val change: Double,
    val columns: List<StackedBarsGlassTooltipDay>,
)

/** The data of the macros screen and the drawing of its columns. */
internal object StackedBarsGlassTooltipMacros {
    // Calories in a gram of each macro.
    private const val KCAL_CARBS = 4.0
    private const val KCAL_FAT = 9.0
    private const val KCAL_PROTEIN = 4.0

    fun calories(day: StackedBarsGlassTooltipDay) = day.carbs * KCAL_CARBS + day.fat * KCAL_FAT + day.protein * KCAL_PROTEIN

    private fun d(label: String, title: String, carbs: Int, fat: Int, protein: Int) = StackedBarsGlassTooltipDay(label, title, carbs, fat, protein)

    private val WEEK = listOf(
        d("Mar 23", "Mar 23, 2020", 210, 52, 110),
        d("Mar 24", "Mar 24, 2020", 160, 46, 128),
        d("Mar 25", "Mar 25, 2020", 245, 44, 120),
        d("Mar 26", "Mar 26, 2020", 128, 62, 165),
        d("Mar 27", "Mar 27, 2020", 176, 38, 96),
        d("Mar 28", "Mar 28, 2020", 150, 74, 150),
        d("Mar 29", "Mar 29, 2020", 168, 50, 112),
    )

    private val DAY = listOf(
        d("7 AM", "Mar 29, 7 AM", 62, 14, 28),
        d("10 AM", "Mar 29, 10 AM", 24, 9, 12),
        d("1 PM", "Mar 29, 1 PM", 48, 12, 36),
        d("4 PM", "Mar 29, 4 PM", 18, 6, 6),
        d("7 PM", "Mar 29, 7 PM", 52, 15, 30),
    )

    /** A fixed hash in [0, 1) of a day and a channel, so March looks the same on every run and stack. */
    private fun hash(day: Int, channel: Int): Double {
        var h = (day * 374761393L + channel * 668265263L).toInt()
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)).toUInt().toDouble() / 4294967296.0
    }

    /** JavaScript's Math.round: halves go up. */
    fun round(value: Double) = floor(value + 0.5)

    // March: the week above in its place, the other days drawn from the hash.
    private val MONTH = (1..31).map { day ->
        val label = if (day % 7 == 1) "Mar $day" else ""
        if (day in 23..29) {
            WEEK[day - 23].copy(label = label)
        } else {
            d(
                label,
                "Mar $day, 2020",
                round(120 + 120 * hash(day, 1)).toInt(),
                round(32 + 40 * hash(day, 2)).toInt(),
                round(80 + 80 * hash(day, 3)).toInt(),
            )
        }
    }

    val RANGES = listOf(
        StackedBarsGlassTooltipRange("day", "Day", "Sunday, Mar 29, 2020", 750.0, 4.1, DAY),
        StackedBarsGlassTooltipRange("week", "Week", "Mar 23 – Mar 29, 2020", 2500.0, 16.2, WEEK),
        StackedBarsGlassTooltipRange("month", "Month", "March 2020", 2500.0, 8.7, MONTH),
    )

    /** A range as the canvas draws it: per column, its calories of protein, fat and carbs. */
    class Bars(val kcal: List<DoubleArray>, val max: Double)

    fun bars(range: StackedBarsGlassTooltipRange) = Bars(
        range.columns.map { doubleArrayOf(it.protein * KCAL_PROTEIN, it.fat * KCAL_FAT, it.carbs * KCAL_CARBS) },
        range.max,
    )

    /** Where the columns sit in a chart [width] wide: the pitch, and each column's width. */
    fun columnLayout(count: Int, width: Float, widest: Float, share: Float): Pair<Float, Float> {
        val pitch = width / count
        return pitch to min(widest, pitch * share)
    }

    class Paints(
        /** Bottom to top: protein, fat, carbs; each a gradient top to bottom. */
        val colors: List<List<Color>>,
        val gap: Float,
        val radius: Float,
        val lift: Float,
        val dim: Float,
        /** The colour under the chart, which a dimmed column fades toward. */
        val card: Color,
        val widest: Float,
        val share: Float,
    )

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /**
     * The stacked columns, in points. During a range switch every column of the larger set is
     * drawn, and each starts (or ends) at the column of the smaller set it shares a place with, so
     * seven wide columns split into thirty-one slim ones and gather back. [focusX] is the hovered
     * column's centre; the others dim by [focus], and the hovered one lifts.
     */
    fun DrawScope.drawBars(from: Bars, to: Bars, morph: Float, focusX: Float, focus: Float, width: Float, height: Float, paints: Paints) {
        val nFrom = from.kcal.size
        val nTo = to.kcal.size
        val many = max(nFrom, nTo)
        val (pitchFrom, wideFrom) = columnLayout(nFrom, width, paints.widest, paints.share)
        val (pitchTo, wideTo) = columnLayout(nTo, width, paints.widest, paints.share)
        val pitchNow = lerp(pitchFrom, pitchTo, morph)
        val top = lerp(from.max.toFloat(), to.max.toFloat(), morph)
        val shaders = paints.colors.map { Brush.verticalGradient(it, startY = 0f, endY = height) }
        val path = Path()
        for (k in 0 until many) {
            // This column's partner in each set: itself in the larger one, the nearest in the smaller.
            val a = if (nFrom == many) k else round(k * (nFrom - 1).toDouble() / max(1, many - 1)).toInt()
            val b = if (nTo == many) k else round(k * (nTo - 1).toDouble() / max(1, many - 1)).toInt()
            val x = lerp((a + 0.5f) * pitchFrom, (b + 0.5f) * pitchTo, morph)
            val w = lerp(wideFrom, wideTo, morph)
            val near = max(0f, 1 - abs(x - focusX) / pitchNow)
            val lift = paints.lift * near * focus
            // Dimming washes a column toward the card's white, which reads the same as fading it.
            val dim = paints.dim * focus * (1 - near)
            var base = height - lift
            for (s in 0 until 3) {
                val kcal = lerp(from.kcal[a][s].toFloat(), to.kcal[b][s].toFloat(), morph)
                val h = kcal / top * height - (if (s > 0) paints.gap else 0f)
                if (h <= 0.5f) continue
                if (s > 0) base -= paints.gap
                val y = base - h
                path.reset()
                if (s == 2) {
                    // Only the top of the stack is rounded.
                    val r = minOf(paints.radius, w / 2, h)
                    path.moveTo(x - w / 2, base)
                    path.lineTo(x - w / 2, y + r)
                    path.arcTo(Rect(Offset(x - w / 2 + r, y + r), r), 180f, 90f, false)
                    path.lineTo(x + w / 2 - r, y)
                    path.arcTo(Rect(Offset(x + w / 2 - r, y + r), r), 270f, 90f, false)
                    path.lineTo(x + w / 2, base)
                    path.close()
                } else {
                    path.addRect(Rect(x - w / 2, y, x + w / 2, base))
                }
                drawPath(path, shaders[s])
                if (dim > 0.01f) drawPath(path, paints.card.copy(alpha = dim))
                base = y
            }
        }
    }

    /** The top of the stack under [x], eased between neighbouring columns, in points from the bottom. */
    fun stackTop(bars: Bars, x: Float, width: Float, height: Float): Float {
        val n = bars.kcal.size
        val f = (x / (width / n) - 0.5f).coerceIn(0f, (n - 1).toFloat())
        val i = floor(f).toInt()
        val j = min(n - 1, i + 1)
        fun total(k: Int) = (bars.kcal[k][0] + bars.kcal[k][1] + bars.kcal[k][2]).toFloat()
        return lerp(total(i), total(j), f - i) / bars.max.toFloat() * height
    }
}
