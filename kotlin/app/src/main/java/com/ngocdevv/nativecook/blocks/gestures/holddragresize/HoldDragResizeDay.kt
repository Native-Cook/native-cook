package com.ngocdevv.nativecook.blocks.gestures.holddragresize

internal object HoldDragResizeKind {
    const val Meeting = 0
    const val Task = 1
    const val Focus = 2
}

internal data class HoldDragResizePlanItem(
    val id: String,
    val kind: Int,
    val start: Double,
    val end: Double,
    val title: String,
    val note: String,
)

private fun holdDragResizeHour(hours: Int, minutes: Int = 0): Double = (hours * 60 + minutes).toDouble()

internal val HoldDragResizePlan = listOf(
    HoldDragResizePlanItem("standup", HoldDragResizeKind.Meeting, holdDragResizeHour(9), holdDragResizeHour(9, 30), "Standup", "Payments squad"),
    HoldDragResizePlanItem("memo", HoldDragResizeKind.Focus, holdDragResizeHour(10), holdDragResizeHour(11), "Pricing memo, first draft", "Focus"),
    HoldDragResizePlanItem("review", HoldDragResizeKind.Task, holdDragResizeHour(11, 15), holdDragResizeHour(11, 45), "Review Ada’s checkout PR", ""),
    HoldDragResizePlanItem("dentist", HoldDragResizeKind.Task, holdDragResizeHour(12), holdDragResizeHour(12, 30), "Call the dentist", ""),
    HoldDragResizePlanItem("lunch", HoldDragResizeKind.Meeting, holdDragResizeHour(13), holdDragResizeHour(14), "Lunch with Maya", "Brindisa, Borough Market"),
    HoldDragResizePlanItem("crit", HoldDragResizeKind.Meeting, holdDragResizeHour(14, 30), holdDragResizeHour(15, 30), "Design crit", "Room 4B · 8 people"),
    HoldDragResizePlanItem("plan", HoldDragResizeKind.Task, holdDragResizeHour(16), holdDragResizeHour(16, 30), "Plan next week", ""),
    HoldDragResizePlanItem("climb", HoldDragResizeKind.Meeting, holdDragResizeHour(17), holdDragResizeHour(17, 45), "Climbing with Jo", "The Castle, Green Lanes"),
)

internal data class HoldDragResizeWeekDay(val day: String, val date: Int, val dots: IntArray)

internal val HoldDragResizeWeek = listOf(
    HoldDragResizeWeekDay("M", 13, intArrayOf(HoldDragResizeKind.Meeting, HoldDragResizeKind.Task)),
    HoldDragResizeWeekDay("T", 14, intArrayOf(HoldDragResizeKind.Focus, HoldDragResizeKind.Meeting, HoldDragResizeKind.Task)),
    HoldDragResizeWeekDay("W", 15, intArrayOf(HoldDragResizeKind.Meeting)),
    HoldDragResizeWeekDay("T", 16, intArrayOf(HoldDragResizeKind.Focus, HoldDragResizeKind.Task, HoldDragResizeKind.Meeting)),
    HoldDragResizeWeekDay("F", 17, intArrayOf(HoldDragResizeKind.Focus, HoldDragResizeKind.Meeting)),
    HoldDragResizeWeekDay("S", 18, intArrayOf(HoldDragResizeKind.Task)),
    HoldDragResizeWeekDay("S", 19, intArrayOf()),
)

internal const val HoldDragResizeToday = 3
internal const val HoldDragResizeDayTitle = "Thursday"
internal const val HoldDragResizeDayDate = "16 October"

internal fun holdDragResizeClock(minutes: Double): String {
    val rounded = kotlin.math.round(minutes).toInt()
    return "%02d:%02d".format(rounded / 60, rounded % 60)
}

internal fun holdDragResizeLength(minutes: Double): String {
    val rounded = kotlin.math.round(minutes).toInt()
    val hours = rounded / 60
    val mins = rounded % 60
    return when {
        hours == 0 -> "$mins min"
        mins == 0 -> "$hours h"
        else -> "$hours h $mins"
    }
}
