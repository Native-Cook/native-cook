package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

/** Feather-equivalent paths keep the port's small icon geometry independent of Material symbols. */
internal fun DrawScope.dragMultiSelectIcon(
    name: DragMultiSelectIcon,
    color: Color,
    strokeWidth: Float,
) {
    val unit = min(size.width, size.height) / 24f
    val left = (size.width - 24f * unit) / 2f
    val top = (size.height - 24f * unit) / 2f
    fun point(x: Float, y: Float) = Offset(left + x * unit, top + y * unit)
    val stroke = Stroke(strokeWidth * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        drawLine(color, point(x1, y1), point(x2, y2), strokeWidth * unit, cap = StrokeCap.Round)
    }
    fun circle(x: Float, y: Float, radius: Float) {
        drawCircle(color, radius * unit, point(x, y), style = stroke)
    }
    fun cubic(shape: Path, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val a = point(x1, y1)
        val b = point(x2, y2)
        val c = point(x3, y3)
        shape.cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
    }
    fun path(vararg points: Float) {
        val shape = Path()
        shape.moveTo(point(points[0], points[1]).x, point(points[0], points[1]).y)
        var index = 2
        while (index < points.size) {
            shape.lineTo(point(points[index], points[index + 1]).x, point(points[index], points[index + 1]).y)
            index += 2
        }
        drawPath(shape, color, style = stroke)
    }

    when (name) {
        DragMultiSelectIcon.Back -> path(15f, 18f, 9f, 12f, 15f, 6f)
        DragMultiSelectIcon.ChevronDown -> path(6f, 9f, 12f, 15f, 18f, 9f)
        DragMultiSelectIcon.FileText -> {
            val shape = Path()
            shape.moveTo(point(14f, 2f).x, point(14f, 2f).y)
            shape.lineTo(point(6f, 2f).x, point(6f, 2f).y)
            cubic(shape, 4.89543f, 2f, 4f, 2.89543f, 4f, 4f)
            shape.lineTo(point(4f, 20f).x, point(4f, 20f).y)
            cubic(shape, 4f, 21.10457f, 4.89543f, 22f, 6f, 22f)
            shape.lineTo(point(18f, 22f).x, point(18f, 22f).y)
            cubic(shape, 19.10457f, 22f, 20f, 21.10457f, 20f, 20f)
            shape.lineTo(point(20f, 8f).x, point(20f, 8f).y)
            shape.close()
            drawPath(shape, color, style = stroke)
            path(14f, 2f, 14f, 8f, 20f, 8f)
            line(8f, 9f, 10f, 9f)
            line(8f, 13f, 16f, 13f)
            line(8f, 17f, 16f, 17f)
        }
        DragMultiSelectIcon.Plus -> {
            line(12f, 5f, 12f, 19f)
            line(5f, 12f, 19f, 12f)
        }
        DragMultiSelectIcon.Calendar -> {
            drawRoundRect(
                color = color,
                topLeft = point(3f, 4f),
                size = Size(18f * unit, 18f * unit),
                cornerRadius = CornerRadius(2f * unit),
                style = stroke,
            )
            line(16f, 2f, 16f, 6f)
            line(8f, 2f, 8f, 6f)
            line(3f, 10f, 21f, 10f)
        }
        DragMultiSelectIcon.ArrowRight -> {
            line(5f, 12f, 19f, 12f)
            path(12f, 5f, 19f, 12f, 12f, 19f)
        }
        DragMultiSelectIcon.Trash -> {
            path(3f, 6f, 5f, 6f, 21f, 6f)
            val bin = Path()
            bin.moveTo(point(19f, 6f).x, point(19f, 6f).y)
            bin.lineTo(point(19f, 20f).x, point(19f, 20f).y)
            cubic(bin, 19f, 21.10457f, 18.10457f, 22f, 17f, 22f)
            bin.lineTo(point(7f, 22f).x, point(7f, 22f).y)
            cubic(bin, 5.89543f, 22f, 5f, 21.10457f, 5f, 20f)
            bin.lineTo(point(5f, 6f).x, point(5f, 6f).y)
            drawPath(bin, color, style = stroke)
            val handle = Path()
            handle.moveTo(point(8f, 6f).x, point(8f, 6f).y)
            handle.lineTo(point(8f, 4f).x, point(8f, 4f).y)
            cubic(handle, 8f, 2.89543f, 8.89543f, 2f, 10f, 2f)
            handle.lineTo(point(14f, 2f).x, point(14f, 2f).y)
            cubic(handle, 15.10457f, 2f, 16f, 2.89543f, 16f, 4f)
            handle.lineTo(point(16f, 6f).x, point(16f, 6f).y)
            drawPath(handle, color, style = stroke)
            line(10f, 11f, 10f, 17f)
            line(14f, 11f, 14f, 17f)
        }
        DragMultiSelectIcon.More -> {
            circle(5f, 12f, 1f)
            circle(12f, 12f, 1f)
            circle(19f, 12f, 1f)
        }
    }
}

internal enum class DragMultiSelectIcon {
    Back,
    ChevronDown,
    FileText,
    Plus,
    Calendar,
    ArrowRight,
    Trash,
    More,
}
