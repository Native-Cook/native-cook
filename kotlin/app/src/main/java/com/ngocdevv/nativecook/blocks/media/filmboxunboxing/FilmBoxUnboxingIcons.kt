package com.ngocdevv.nativecook.blocks.media.filmboxunboxing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Line icons in a 24-dp box: open strokes, closed shapes and circles. */
internal class FilmDrawing(
    val lines: List<FloatArray> = emptyList(),
    val shapes: List<FloatArray> = emptyList(),
    val circles: List<FloatArray> = emptyList(),
)

private val BoltShape = floatArrayOf(13f, 3f, 6f, 13.5f, 11.5f, 13.5f, 10.5f, 21f, 18f, 10.5f, 12.5f, 10.5f)

internal object FilmIcons {
    val Arrow = FilmDrawing(lines = listOf(floatArrayOf(5f, 12f, 19f, 12f), floatArrayOf(13f, 6f, 19f, 12f, 13f, 18f)))
    val Timer = FilmDrawing(
        lines = listOf(floatArrayOf(12f, 13.5f, 12f, 9.5f), floatArrayOf(9.5f, 3f, 14.5f, 3f), floatArrayOf(12f, 3f, 12f, 6f)),
        circles = listOf(floatArrayOf(12f, 13.5f, 7.5f)),
    )
    val FlashOff = FilmDrawing(lines = listOf(floatArrayOf(4f, 4f, 20f, 20f)), shapes = listOf(BoltShape))
    val FlashOn = FilmDrawing(shapes = listOf(BoltShape))
    val Download = FilmDrawing(lines = listOf(floatArrayOf(12f, 4f, 12f, 15f), floatArrayOf(7.5f, 10.5f, 12f, 15f, 16.5f, 10.5f), floatArrayOf(5f, 19.5f, 19f, 19.5f)))
    val Share = FilmDrawing(
        lines = listOf(floatArrayOf(12f, 3f, 12f, 14f), floatArrayOf(8f, 7f, 12f, 3f, 16f, 7f), floatArrayOf(8.5f, 10f, 6f, 10f, 6f, 20.5f, 18f, 20.5f, 18f, 10f, 15.5f, 10f)),
    )
}

private fun outline(points: FloatArray, k: Float, closed: Boolean) = Path().apply {
    moveTo(points[0] * k, points[1] * k)
    for (i in 2 until points.size step 2) lineTo(points[i] * k, points[i + 1] * k)
    if (closed) close()
}

/** [fill] fills the closed shapes (the flash when it is on). */
@Composable
internal fun FilmIcon(drawing: FilmDrawing, size: Float, color: Color, stroke: Float, fill: Color? = null) {
    Canvas(Modifier.size(size.dp)) {
        val k = this.size.width / 24f
        val style = Stroke(width = stroke * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (c in drawing.circles) drawCircle(color, c[2] * k, Offset(c[0] * k, c[1] * k), style = style)
        for (shape in drawing.shapes) {
            val path = outline(shape, k, closed = true)
            if (fill != null) drawPath(path, fill)
            drawPath(path, color, style = style)
        }
        for (line in drawing.lines) drawPath(outline(line, k, closed = false), color, style = style)
    }
}
