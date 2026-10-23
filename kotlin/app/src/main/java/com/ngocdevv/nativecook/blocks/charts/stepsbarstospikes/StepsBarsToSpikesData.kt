package com.ngocdevv.nativecook.blocks.charts.stepsbarstospikes

import java.util.Locale
import kotlin.math.floor

/** One switch position of the chart: a bar per day or week, the last one being now. */
class StepsBarsToSpikesView(
    /** The switch's letter. */
    val key: String,
    /** Steps per bar, oldest first. */
    val values: List<Double>,
    /** A label under each bar; an empty one leaves the bar unlabelled. */
    val labels: List<String>,
    /** Each bar's name, shown under the number while a finger is on it. */
    val names: List<String>,
    /** The bar that is now, drawn solid. */
    val current: Int,
    /** The top of the value axis. */
    val max: Double,
    /** Steps in the same stretch of the period before, for the change chip. */
    val previous: Double,
    /** The total of this period, which the big number shows. */
    val total: Double,
    val changeLabel: String,
    /** The small figure at the top left and what it means. */
    val note: Double,
    val noteLabel: String,
    /** Shown at the top right of the chart, e.g. the month. */
    val badge: String? = null,
)

internal object StepsBarsToSpikesData {
    // October 2026 up to today, the 13th.
    private val OCTOBER = listOf(4400.0, 5900.0, 7600.0, 6200.0, 9100.0, 7800.0, 7020.0, 8730.0, 4050.0, 3600.0, 0.0, 4950.0, 2369.0)
    private const val MONTH_DAYS = 31
    private const val DAILY_GOAL = 5132.0

    /** JavaScript's Math.round: halves go up. */
    fun round(value: Double) = floor(value + 0.5)

    private val lastWeek = OCTOBER.takeLast(7)
    private val thisWeek = OCTOBER.drop(11).sum()

    val VIEWS = listOf(
        StepsBarsToSpikesView(
            key = "D",
            values = lastWeek,
            labels = listOf("Wed", "Thu", "Fri", "Sat", "Sun", "Mon", "Tue"),
            names = listOf("Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "Monday", "Today"),
            current = 6,
            max = 9000.0,
            previous = lastWeek[5],
            total = lastWeek[6],
            changeLabel = "than previous day",
            note = DAILY_GOAL - lastWeek[6],
            noteLabel = "Steps behind",
        ),
        StepsBarsToSpikesView(
            key = "W",
            values = listOf(46200.0, 51800.0, 39400.0, 55600.0, 48900.0, OCTOBER.subList(4, 11).sum(), thisWeek),
            labels = listOf("8/31", "9/7", "9/14", "9/21", "9/28", "10/5", "10/12"),
            names = listOf("Week of Aug 31", "Week of Sep 7", "Week of Sep 14", "Week of Sep 21", "Week of Sep 28", "Week of Oct 5", "This week"),
            current = 6,
            max = 63000.0,
            previous = OCTOBER.subList(4, 6).sum(),
            total = thisWeek,
            changeLabel = "than previous week",
            note = round(thisWeek / 2),
            noteLabel = "Avg. steps per day",
        ),
        StepsBarsToSpikesView(
            key = "M",
            values = OCTOBER + List(MONTH_DAYS - OCTOBER.size) { 0.0 },
            labels = List(MONTH_DAYS) { if (it % 4 == 0) (it + 1).toString().padStart(2, '0') else "" },
            names = List(MONTH_DAYS) { if (it == OCTOBER.size - 1) "Today" else "Oct ${it + 1}" },
            current = OCTOBER.size - 1,
            max = 10000.0,
            previous = 84600.0,
            total = OCTOBER.sum(),
            changeLabel = "than previous month",
            note = round(OCTOBER.sum() / OCTOBER.size),
            noteLabel = "Avg. steps per day",
            badge = "OCT '26",
        ),
    )

    // What the steps come to, at an average stride and weight.
    private const val KM_PER_STEP = 0.00071
    private const val KCAL_PER_STEP = 0.0904
    private const val STEPS_PER_FLOOR = 590.0

    class Stats(val distance: String, val calories: String, val floors: String)

    fun statsOf(steps: Double) = Stats(
        distance = String.format(Locale.US, "%.2f", steps * KM_PER_STEP),
        calories = grouped(String.format(Locale.US, "%.1f", steps * KCAL_PER_STEP)),
        floors = round(steps / STEPS_PER_FLOOR).toLong().toString(),
    )

    /** "87291" → "87,291", keeping any decimals. */
    fun grouped(value: String): String {
        val whole = value.substringBefore('.')
        val out = StringBuilder()
        whole.forEachIndexed { i, character ->
            if (i > 0 && (whole.length - i) % 3 == 0) out.append(',')
            out.append(character)
        }
        return if ('.' in value) "$out.${value.substringAfter('.')}" else out.toString()
    }

    fun grouped(value: Double) = grouped(value.toLong().toString())

    /** An axis value: 8100 → "8.1K", 900 → "900". */
    fun axisLabel(value: Double): String {
        if (value < 1000) return round(value).toLong().toString()
        return String.format(Locale.US, "%.1f", value / 1000).removeSuffix(".0") + "K"
    }
}
