package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar.StampDiaryCalendarMotion as M
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle

/** Pixels to dp / sp that stay exact pixels (the pin does not follow the user's font scale). */
@Composable
internal fun pxToDp(px: Double): Dp = with(LocalDensity.current) { px.toFloat().toDp() }

@Composable
internal fun pxToSp(px: Double): TextUnit {
    val density = LocalDensity.current
    return (px / (density.density * density.fontScale)).toFloat().sp
}

/** Weekday band and the dated cells (the still part): numbers, and accessibility for every day. */
@Composable
internal fun StampDiaryCalendarDays(
    space: StampDiaryCalendarSpace,
    month: M.Month,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = StampDiaryCalendarTheme
    val s = space.s
    val may = M.isMay2026(month)
    Box(modifier.fillMaxSize()) {
        t.WEEKDAYS.forEachIndexed { i, label ->
            Box(
                Modifier
                    .offset { IntOffset((i * t.COLUMN_WIDTH * s).toInt(), space.y(t.WEEKDAY_TOP).toInt()) }
                    .size(pxToDp(t.COLUMN_WIDTH * s), pxToDp((t.GRID_TOP - t.WEEKDAY_TOP) * s))
                    .semantics { contentDescription = "" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = StampDiaryCalendarColors.weekday,
                    fontSize = pxToSp(t.WEEKDAY_SIZE * s),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        for (day in 1..M.daysIn(month)) {
            val cell = M.cellOf(day, month)
            val memory = if (may) M.memoryIndex(day, month) else -1
            val label = t.MONTH_NAMES[month.month - 1] + " $day, ${month.year}" + if (memory >= 0) ", memory" else ""
            Box(
                Modifier
                    .offset { IntOffset((cell.column * t.COLUMN_WIDTH * s).toInt(), space.y(t.GRID_TOP + cell.row * t.ROW_HEIGHT).toInt()) }
                    .size(pxToDp(t.COLUMN_WIDTH * s), pxToDp(t.ROW_HEIGHT * s))
                    .semantics(mergeDescendants = true) {
                        contentDescription = label
                        role = Role.Button
                        onClick {
                            onSelect(day)
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (memory < 0) DayNumber(day, s)
            }
        }
    }
}

@Composable
private fun DayNumber(day: Int, s: Double) {
    Text(
        day.toString(),
        color = StampDiaryCalendarColors.day,
        fontSize = pxToSp(StampDiaryCalendarTheme.DAY_SIZE * s),
        fontWeight = FontWeight.SemiBold,
        style = TextStyle(fontFeatureSettings = "tnum"),
    )
}

/** The day numbers of the emptied cells and the selection rings, driven by the frame (layer phase only). */
@Composable
internal fun StampDiaryCalendarReveal(
    state: StampDiaryCalendarState,
    space: StampDiaryCalendarSpace,
    modifier: Modifier = Modifier,
) {
    val t = StampDiaryCalendarTheme
    val s = space.s
    val month = state.month
    Box(modifier.fillMaxSize()) {
        if (M.isMay2026(month)) {
            StampDiaryCalendarData.memories.forEachIndexed { index, mem ->
                val c = M.cellOf(mem.day, M.MAY_2026)
                Box(
                    Modifier
                        .offset { IntOffset((c.column * t.COLUMN_WIDTH * s).toInt(), space.y(t.GRID_TOP + c.row * t.ROW_HEIGHT).toInt()) }
                        .size(pxToDp(t.COLUMN_WIDTH * s), pxToDp(t.ROW_HEIGHT * s))
                        .graphicsLayer { alpha = if (state.pose.memory == index) state.pose.reveal.toFloat() else 0f },
                    contentAlignment = Alignment.Center,
                ) {
                    DayNumber(mem.day, s)
                    Ring(s)
                }
            }
        }
        val empty = state.emptyDay
        if (empty > 0 && empty <= M.daysIn(month)) {
            val c = M.cellOf(empty, month)
            Box(
                Modifier
                    .offset { IntOffset((c.column * t.COLUMN_WIDTH * s).toInt(), space.y(t.GRID_TOP + c.row * t.ROW_HEIGHT).toInt()) }
                    .size(pxToDp(t.COLUMN_WIDTH * s), pxToDp(t.ROW_HEIGHT * s))
                    .graphicsLayer { alpha = state.emptyAlpha },
            ) { Ring(s) }
        }
    }
}

@Composable
private fun Ring(s: Double) {
    val t = StampDiaryCalendarTheme
    Box(
        Modifier
            .fillMaxSize()
            .padding(pxToDp(t.SELECTION_INSET * s))
            .border(
                t.SELECTION_LINE.dp,
                StampDiaryCalendarColors.selection,
                RoundedCornerShape(pxToDp(t.SELECTION_RADIUS * s)),
            ),
    )
}
