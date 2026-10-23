package com.ngocdevv.nativecook.blocks.lists.badgevaultgrid

import androidx.compose.ui.graphics.Color

internal data class BadgeVaultStat(val value: String, val label: String)

internal data class BadgeVaultGoal(val have: Int, val need: Int, val unit: String)

internal data class BadgeVaultBadge(
    val id: String,
    val name: String,
    val tile: Int,
    val a: FloatArray,
    val b: FloatArray,
    val c: FloatArray,
    val earned: String?,
    val goal: BadgeVaultGoal?,
    val blurb: String,
    val rarity: Int,
    val stats: List<BadgeVaultStat>,
)

internal data class BadgeVaultLayout(
    val width: Float,
    val height: Float,
    val safeTop: Float,
    val safeBottom: Float,
) {
    val scale = minOf(1f, width / 402f)
    val compact = height < 760f
    val headerHeight = safeTop + 125f
    val pitch = halfPoint(196f * scale)
    val radius = halfPoint(50f * scale)
    val badgeY = halfPoint(64f * scale)
    val canvasHeight = halfPoint(136f * scale)
    val nameY = halfPoint(132f * scale)
    val dateY = halfPoint(152f * scale)
    val barY = halfPoint(176f * scale)
    val footerY = headerHeight + 6f * pitch
    val footerHeight = 96f + safeBottom
    val contentHeight = footerY + footerHeight
    val scrollEnd = maxOf(0f, contentHeight - height)
    val heldX = halfPoint(width / 2f)
    val heldY = if (compact) 170f else 300f
    val heldR = if (compact) 96f else 120f
    val titleSize = if (compact) 30f else 32f
    val chipY = if (compact) 292f else 456f
    val titleY = if (compact) 328f else 496f
    val earnedY = if (compact) 368f else 538f
    val blurbY = if (compact) 392f else 566f
    val statsY = if (compact) 452f else 632f
    val hintY = if (compact) null else 724f
    val shareY = height - safeBottom - 72f

    fun columnLeft(column: Int) = halfPoint(16f + (width - 32f) * column / 3f)
    fun columnRight(column: Int) = halfPoint(16f + (width - 32f) * (column + 1) / 3f)
    fun columnWidth(column: Int) = columnRight(column) - columnLeft(column)
    fun rowTop(index: Int) = headerHeight + (index / 3) * pitch - scrollY

    var scrollY = 0f
}

internal data class BadgeVaultPose(
    var cx: Float = 0f,
    var cy: Float = 0f,
    var r: Float = 1f,
    var tiltX: Float = 0f,
    var tiltY: Float = 0f,
    var spin: Float = 0f,
    var roll: Float = 0f,
    var envX: Float = 0f,
    var envY: Float = 0f,
    var sheen: Float = -1f,
    var fill: Float = 1f,
    var wave: Float = 0f,
    var time: Float = 0f,
    var lift: Float = 0f,
    var present: Float = 1f,
    var socket: Float = 1f,
)

internal fun halfPoint(value: Float) = kotlin.math.round(value * 2f) / 2f

internal fun rgb(hex: String): FloatArray {
    val value = hex.removePrefix("#").toInt(16)
    return floatArrayOf(
        ((value shr 16) and 255) / 255f,
        ((value shr 8) and 255) / 255f,
        (value and 255) / 255f,
    )
}

internal fun FloatArray.color(alpha: Float = 1f) = Color(this[0], this[1], this[2], alpha)

private fun badge(
    id: String,
    name: String,
    tile: Int,
    a: String,
    b: String,
    c: String,
    earned: String?,
    goal: BadgeVaultGoal?,
    blurb: String,
    rarity: Int,
    stats: List<String>,
) = BadgeVaultBadge(
    id = id,
    name = name,
    tile = tile,
    a = rgb(a),
    b = rgb(b),
    c = rgb(c),
    earned = earned,
    goal = goal,
    blurb = blurb,
    rarity = rarity,
    stats = stats.chunked(2).map { BadgeVaultStat(it[0], it[1]) },
)

internal val BadgeVaultBadges = listOf(
    badge("first-light", "First Light", 0, "#FF7A2F", "#C7352B", "#FFE08A", "9 Jan 2026", null, "Twenty sessions started before sunrise.", 12, listOf("06:02", "Earliest", "20", "Sessions", "4 wk", "Span")),
    badge("night-shift", "Night Shift", 1, "#3D45E0", "#1A1D6E", "#F6F1DC", "23 Jan 2026", null, "Ten sessions finished after midnight.", 9, listOf("00:47", "Latest", "10", "Sessions", "3 wk", "Span")),
    badge("summit", "Summit", 2, "#1CC4E8", "#0D6FA3", "#FFFFFF", "2 Feb 2026", null, "Climbed 8,848 metres in a single month.", 6, listOf("8,848 m", "Climbed", "17", "Days", "612 m", "Best day")),
    badge("spark", "Spark", 3, "#FFD21F", "#FF9A1F", "#FFFFFF", "14 Feb 2026", null, "Fifty quick sessions under five minutes.", 21, listOf("50", "Sessions", "3:40", "Average", "9", "Best day")),
    badge("deep-dive", "Deep Dive", 4, "#14D8B2", "#0A8A8E", "#D9FFF5", "1 Mar 2026", null, "Four hours of focus without one break.", 4, listOf("4:12 h", "Longest", "31", "Sessions", "96%", "Finished")),
    badge("iron-month", "Iron Month", 5, "#9BE21A", "#3F9E12", "#F2FFD0", null, BadgeVaultGoal(12, 30, "days"), "Show up thirty days in a row.", 3, listOf("12", "Days so far", "18", "To go", "30", "Target")),
    badge("trailhead", "Trailhead", 6, "#FF4D6D", "#B3123F", "#FFE7EC", "19 Mar 2026", null, "Tried every kind of session at least once.", 17, listOf("8", "Kinds", "11", "Days", "2", "Favourites")),
    badge("seven-up", "Seven Up", 7, "#A45BFF", "#5E2BC4", "#F0E5FF", "30 Mar 2026", null, "Seven sessions in a single day.", 8, listOf("7", "Sessions", "3:05 h", "Total", "26 min", "Average")),
    badge("bullseye", "Bullseye", 8, "#FF3B30", "#B01212", "#FFFFFF", "11 Apr 2026", null, "Hit the weekly goal exactly, to the minute.", 2, listOf("300", "Minutes", "0", "Over", "5", "Sessions")),
    badge("evergreen", "Evergreen", 9, "#2ED158", "#0F7A3B", "#D9FFE3", "2 May 2026", null, "A hundred days without missing a week.", 11, listOf("100", "Days", "15", "Weeks", "71", "Sessions")),
    badge("long-haul", "Long Haul", 10, "#FF9F0A", "#B85C00", "#FFF1D6", "28 May 2026", null, "A thousand kilometres, one step at a time.", 5, listOf("1,000 km", "Distance", "148", "Days", "6.8 km", "Average")),
    badge("gemcutter", "Gemcutter", 11, "#5AC8FA", "#1E78D6", "#FFFFFF", "16 Jun 2026", null, "Beat a personal best five times running.", 7, listOf("5", "Records", "12 d", "Span", "+9%", "Gain")),
    badge("hot-streak", "Hot Streak", 12, "#FF5E3A", "#D11A2A", "#FFC83D", "4 Jul 2026", null, "Fourteen days, and the streak is alive.", 14, listOf("14", "Days", "19", "Sessions", "41 min", "Average")),
    badge("orbit", "Orbit", 13, "#7D5CFF", "#2C1E8F", "#FFD86B", "21 Aug 2026", null, "Three hundred and sixty-five sessions.", 1, listOf("365", "Sessions", "212", "Days", "1.7", "Per day")),
    badge("full-week", "Full Week", 14, "#FF2D92", "#A3125E", "#FFE0F0", null, BadgeVaultGoal(6, 7, "days"), "Seven days out of seven.", 19, listOf("7", "Days", "9", "Sessions", "38 min", "Average")),
    badge("steady", "Steady", 15, "#1FB6FF", "#0A4FA8", "#FFFFFF", "9 Sep 2026", null, "Same hour, every day, for three weeks.", 6, listOf("07:30", "Hour", "21", "Days", "±4 min", "Drift")),
    badge("crowned", "Crowned", 16, "#FFC531", "#D98A00", "#FFF6D0", null, BadgeVaultGoal(2, 5, "wins"), "Finish first in five weekly leagues.", 1, listOf("2", "Wins so far", "3", "To go", "5", "Target")),
    badge("keystone", "Keystone", 17, "#34E0C2", "#128F7C", "#EFFFFA", "1 Oct 2026", null, "Completed every badge of one set.", 3, listOf("6", "Badges", "1", "Set", "9 mo", "Span")),
)

internal const val BadgeVaultUnlockDate = "13 Oct 2026"
internal val BadgeVaultCountWords = listOf(
    "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
    "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen",
)
