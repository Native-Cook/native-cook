package com.ngocdevv.nativecook.blocks.sheets.keyrevealsheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

// The four corners of a face-scan frame in an 18 x 18 box, each about 12 dp of line.
private val Corners = listOf(
    Path().apply {
        moveTo(1f, 6f); lineTo(1f, 3f); arcTo(Rect(1f, 1f, 5f, 5f), 180f, 90f, false); lineTo(6f, 1f)
    },
    Path().apply {
        moveTo(12f, 1f); lineTo(15f, 1f); arcTo(Rect(13f, 1f, 17f, 5f), 270f, 90f, false); lineTo(17f, 6f)
    },
    Path().apply {
        moveTo(17f, 12f); lineTo(17f, 15f); arcTo(Rect(13f, 13f, 17f, 17f), 0f, 90f, false); lineTo(12f, 17f)
    },
    Path().apply {
        moveTo(6f, 17f); lineTo(3f, 17f); arcTo(Rect(1f, 13f, 5f, 17f), 90f, 90f, false); lineTo(1f, 12f)
    },
)
private const val CORNER_LENGTH = 12f
private const val SIZE = 18f
// Scan 0 to 1: the corners draw in over the first part, a line sweeps across the rest.
private const val DRAW_BY = 0.4f
private const val LINE_FROM = 4f
private const val LINE_TO = 14f

/** A face-scan icon whose corners draw themselves and a line sweeps across while [scan] runs 0 to 1. */
@Composable
internal fun FaceScan(scan: () -> Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(SIZE.dp)) {
        val s = scan()
        scale(size.width / SIZE, size.height / SIZE, pivot = Offset.Zero) {
            val corners = Stroke(
                1.8f, cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(CORNER_LENGTH, CORNER_LENGTH), CORNER_LENGTH * (1f - min(1f, s / DRAW_BY))),
            )
            for (corner in Corners) drawPath(corner, color, style = corners)
            val sweep = min(1f, max(0f, (s - 0.15f) / 0.8f))
            val x = LINE_FROM + (LINE_TO - LINE_FROM) * sweep
            val alpha = if (s >= 1f) 0f else min(1f, sweep * 6f)
            drawLine(color.copy(alpha = alpha), Offset(x, 5f), Offset(x, 13f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        }
    }
}
