package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.ceil
import kotlin.math.floor

internal object StampDiaryCalendarColors {
    val page = Color(0xFF0B0B0B)
    val heroTop = Color(0xFF010101)
    val heroBottom = Color(0xFF060606)
    val heroLine = Color.White.copy(alpha = 0.05f)
    val blank = Color(0xFF111111)
    val gridLine = Color(0xFF222222)
    val weekday = Color(0xFF737373)
    val day = Color.White
    val year = Color(0xFF9A9A9A)
    val month = Color.White
    val headerFill = Color(36, 36, 36, (0.55f * 255).toInt())
    val headerIcon = Color.White
    val selection = Color.White.copy(alpha = 0.72f)
    val menuFill = Color(30, 30, 30, (0.6f * 255).toInt())
    val menuDivider = Color.White.copy(alpha = 0.1f)
}

/** Everything still: the hero's graph paper and the calendar's tiles and lines. Drawn once. */
@androidx.compose.runtime.Composable
internal fun StampDiaryCalendarBackdrop(space: StampDiaryCalendarSpace, month: StampDiaryCalendarMotion.Month, modifier: Modifier = Modifier) {
    val t = StampDiaryCalendarTheme
    val m = StampDiaryCalendarMotion
    Canvas(modifier.fillMaxSize()) {
        val s = space.s.toFloat()
        val oy = space.oyPx.toFloat()
        val first = m.firstWeekday(month)
        val count = m.daysIn(month)
        val paperRows = floor((t.HERO_BOTTOM - t.PAPER_PHASE_Y) / t.PAPER_PITCH).toInt() + 1
        val paperCols = ceil(t.REF_WIDTH / t.PAPER_PITCH).toInt() + 1
        val bottom = t.GRID_TOP + t.ROWS * t.ROW_HEIGHT
        drawRect(StampDiaryCalendarColors.page, Offset.Zero, size)
        translate(0f, oy) {
            scale(s, s, pivot = Offset.Zero) {
                fun rect(x: Double, y: Double, w: Double, h: Double, color: Color) =
                    drawRect(color, Offset(x.toFloat(), y.toFloat()), Size(w.toFloat(), h.toFloat()))
                val top = -oy / s
                // Hero backdrop, from the top of the screen to where the weekday band starts.
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(StampDiaryCalendarColors.heroTop, StampDiaryCalendarColors.heroBottom),
                        startY = 0f,
                        endY = t.HERO_BOTTOM.toFloat(),
                    ),
                    Offset(0f, top), Size(t.REF_WIDTH.toFloat(), t.HERO_BOTTOM.toFloat() - top),
                )
                for (i in 0 until paperCols) {
                    rect(t.PAPER_PHASE_X - t.PAPER_PITCH + i * t.PAPER_PITCH - 0.5, top.toDouble(), 1.0, t.HERO_BOTTOM - top, StampDiaryCalendarColors.heroLine)
                }
                for (i in 0 until paperRows) {
                    rect(0.0, t.PAPER_PHASE_Y + i * t.PAPER_PITCH - 0.5, t.REF_WIDTH, 1.0, StampDiaryCalendarColors.heroLine)
                }
                for (i in 0 until t.ROWS * 7) {
                    val day = i - first + 1
                    if (day in 1..count) continue
                    rect((i % 7) * t.COLUMN_WIDTH, t.GRID_TOP + (i / 7) * t.ROW_HEIGHT, t.COLUMN_WIDTH, t.ROW_HEIGHT, StampDiaryCalendarColors.blank)
                }
                for (i in 0 until 8) {
                    rect(i * t.COLUMN_WIDTH - t.GRID_LINE / 2, t.WEEKDAY_TOP, t.GRID_LINE, bottom - t.WEEKDAY_TOP, StampDiaryCalendarColors.gridLine)
                }
                for (i in 0 until t.ROWS + 2) {
                    val y = if (i == 0) t.WEEKDAY_TOP else t.GRID_TOP + (i - 1) * t.ROW_HEIGHT
                    rect(0.0, y - t.GRID_LINE / 2, t.REF_WIDTH, t.GRID_LINE, StampDiaryCalendarColors.gridLine)
                }
            }
        }
    }
}
