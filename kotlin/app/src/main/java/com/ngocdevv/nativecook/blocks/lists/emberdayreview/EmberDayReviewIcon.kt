package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val ICON_UNITS = 512f

/** The shackle of the closed lock, in Ionicons' 512-unit space (`M336 208v-95a80 80 0 00-160 0v95`). */
private const val LOCK_CLOSED_SHACKLE = "M336 208v-95a80 80 0 00-160 0v95"

/** The shackle of the open lock: its right end is lifted out of the body (`M336 112a80 80 0 00-160 0v96`). */
private const val LOCK_OPEN_SHACKLE = "M336 112a80 80 0 00-160 0v96"

/** One Ionicons glyph at [size] (the `size` prop of `Ionicons`) in [color]. */
@Composable
internal fun EmberDayReviewIcon(glyph: EmberDayReviewGlyph, size: Dp, color: Color, modifier: Modifier = Modifier) {
    val shackle = remember(glyph) {
        when (glyph) {
            EmberDayReviewGlyph.LockClosed -> PathParser().parsePathString(LOCK_CLOSED_SHACKLE).toPath()
            EmberDayReviewGlyph.LockOpen -> PathParser().parsePathString(LOCK_OPEN_SHACKLE).toPath()
            else -> Path()
        }
    }
    Canvas(modifier.size(size)) {
        val unit = this.size.width / ICON_UNITS
        scale(unit, unit, pivot = Offset.Zero) {
            when (glyph) {
                EmberDayReviewGlyph.Checkmark -> drawPolyline(color, 32f, 416f, 128f, 192f, 384f, 96f, 288f)
                EmberDayReviewGlyph.Add -> {
                    drawPolyline(color, 32f, 256f, 112f, 256f, 400f)
                    drawPolyline(color, 32f, 400f, 256f, 112f, 256f)
                }
                EmberDayReviewGlyph.ArrowForward -> {
                    drawPolyline(color, 48f, 268f, 112f, 412f, 256f, 268f, 400f)
                    drawPolyline(color, 48f, 392f, 256f, 100f, 256f)
                }
                EmberDayReviewGlyph.EllipsisHorizontal -> {
                    drawCircle(color, 48f, Offset(256f, 256f))
                    drawCircle(color, 48f, Offset(416f, 256f))
                    drawCircle(color, 48f, Offset(96f, 256f))
                }
                EmberDayReviewGlyph.LockClosed,
                EmberDayReviewGlyph.LockOpen -> {
                    drawPath(shackle, color, style = Stroke(32f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawRoundRect(color, Offset(96f, 208f), Size(320f, 272f), CornerRadius(48f))
                }
            }
        }
    }
}

/** A round-capped, round-joined line through [points] (x, y pairs), the way Ionicons strokes its outline glyphs. */
private fun DrawScope.drawPolyline(color: Color, width: Float, vararg points: Float) {
    val path = Path()
    path.moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        path.lineTo(points[i], points[i + 1])
        i += 2
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
