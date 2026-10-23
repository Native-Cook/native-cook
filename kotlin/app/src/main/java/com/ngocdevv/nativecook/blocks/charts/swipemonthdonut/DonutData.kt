package com.ngocdevv.nativecook.blocks.charts.swipemonthdonut

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow

/** @param icon An emoji drawn by the system font. */
data class Category(val name: String, val icon: String, val color: Color)

/**
 * @param amounts Spent per category, in the order of the categories; 0 leaves the category out of that month.
 * @param counts How many payments make up each amount.
 * @param change The change from the month before, shown under the total.
 */
data class Month(val label: String, val amounts: List<Float>, val counts: List<Int>, val change: Change) {
    data class Change(val amount: Float, val percent: Int, val up: Boolean)

    val total: Float get() = amounts.sum()
}

// The ring keeps this order clockwise from the top, so a category always grows out of the same place.
val Categories = listOf(
    Category("Eating Out", "🍽️", Color(0xFF7165F0)),
    Category("Subscriptions", "📅", Color(0xFFF0506A)),
    Category("Groceries", "🥦", Color(0xFFFBA05A)),
    Category("Clothing", "👕", Color(0xFFC4C4CC)),
    Category("Coffee", "☕", Color(0xFFDADAE0)),
    Category("Drinks", "🍹", Color(0xFFE6E6EA)),
)

val Months = listOf(
    Month("November 2024", listOf(100f, 104f, 0f, 0f, 0f, 0f), listOf(4, 4, 0, 0, 0, 0), Month.Change(51f, 20, false)),
    Month("December 2024", listOf(100f, 104f, 133f, 63f, 9f, 0f), listOf(4, 4, 6, 2, 3, 0), Month.Change(205f, 100, true)),
    Month("January 2025", listOf(100f, 104f, 35f, 63f, 0f, 42f), listOf(5, 4, 2, 1, 0, 3), Month.Change(65f, 15, false)),
)

// autoPlay, in seconds from the start of a loop: November rests, the pager swipes to December and
// January, one segment is tapped there, then it swipes back twice.
const val LOOP = 8.6f

/** One swipe: when it starts, the page it leaves for, and how long it takes. */
internal class Swipe(val at: Float, val to: Float, val duration: Float)

internal val Swipes = listOf(Swipe(0.9f, 1f, 0.7f), Swipe(3.4f, 2f, 0.7f), Swipe(6.0f, 1f, 0.8f), Swipe(6.8f, 0f, 0.8f))

// In January the second category is tapped and let go.
internal const val FOCUS_ON = 4.6f
internal const val FOCUS_OFF = 5.6f
internal const val FOCUS_CATEGORY = 1

// After a touch autoPlay waits this long, settles back to the first page, and plays again.
internal const val USER_HOLD = 3f
internal const val RETURN_FOR = 0.6f

internal fun clamp01(t: Float) = t.coerceIn(0f, 1f)

internal fun easeInOut(t: Float): Float {
    val u = clamp01(t)
    return if (u < 0.5f) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
}

/** 0 to 1 with one soft overshoot, the shape of the pop spring, for autoPlay's tap. */
internal fun springOut(t: Float): Float {
    val u = clamp01(t)
    return 1 - (1 - u).pow(3) * cos(u * 4)
}

/** Where the pager is [t] seconds into a loop: a page number, fractional during a swipe. */
internal fun pageAt(t: Float): Float {
    var page = 0f
    for (i in Swipes.indices) {
        val swipe = Swipes[i]
        if (t < swipe.at) break
        val from = if (i == 0) 0f else Swipes[i - 1].to
        page = from + (swipe.to - from) * easeInOut((t - swipe.at) / swipe.duration)
    }
    return page
}

/** Each month's share of every category, from 0 to 1. */
internal fun shares(months: List<Month>) = months.map { month ->
    val total = month.total
    month.amounts.map { if (total > 0) it / total else 0f }
}
