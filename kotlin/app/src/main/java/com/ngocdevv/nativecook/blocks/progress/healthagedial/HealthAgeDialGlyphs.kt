package com.ngocdevv.nativecook.blocks.progress.healthagedial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal enum class HealthAgeDialPetalShape {
    Chip,
    Tab,
}

internal val OVERVIEW_FILLS: List<Color?> = listOf(
    Color(0xFF43B93A),
    Color(0xFFEE5A2C),
    Color(0xFFE5285B),
    Color(0xFFCF2DC0),
    null,
    Color(0xFF6A3FD0),
    Color(0xFF1E7DE2),
    Color(0xFF18AE9C),
)

private fun healthAgeDialPoint(
    center: Offset,
    radius: Float,
    angle: Float,
    rotation: Float,
): Offset {
    return Offset(
        x = center.x + sin(angle + rotation) * radius,
        y = center.y - cos(angle + rotation) * radius,
    )
}

private fun DrawScope.drawPetalMark(
    fills: List<Color?>,
    tabShape: Boolean,
) {
    val scale = size.width / 36f
    val center = Offset(size.width / 2f, size.height / 2f)
    val inner = (if (tabShape) 4.2f else 5.4f) * scale
    val outer = (if (tabShape) 14.8f else 14.6f) * scale
    val half = (if (tabShape) 15.5f else 13.5f) * PI.toFloat() / 180f
    fills.forEachIndexed { index, fill ->
        if (fill != null) {
            val rotation = index * PI.toFloat() / 4f
            val path = Path()
            val innerStart = healthAgeDialPoint(center, inner, -half * 0.55f, rotation)
            val outerStart = healthAgeDialPoint(center, outer, -half, rotation)
            path.moveTo(innerStart.x, innerStart.y)
            path.lineTo(outerStart.x, outerStart.y)
            path.arcTo(
                rect = Rect(center.x - outer, center.y - outer, center.x + outer, center.y + outer),
                startAngleDegrees = (-90f - half * 180f / PI.toFloat() + rotation * 180f / PI.toFloat()),
                sweepAngleDegrees = 2f * half * 180f / PI.toFloat(),
                forceMoveTo = false,
            )
            val innerEnd = healthAgeDialPoint(center, inner, half * 0.55f, rotation)
            path.lineTo(innerEnd.x, innerEnd.y)
            path.close()
            drawPath(path, fill)
            drawPath(
                path = path,
                color = fill,
                style = Stroke(width = 2.2f * scale, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
internal fun HealthAgeDialPetalMark(
    size: Double,
    fills: List<Color?>,
    tabShape: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size.toFloat().dp)) {
        drawPetalMark(fills = fills, tabShape = tabShape)
    }
}

@Composable
internal fun PetalMark(
    size: Double,
    fills: List<Color?>,
    shape: HealthAgeDialPetalShape = HealthAgeDialPetalShape.Chip,
    modifier: Modifier = Modifier,
) {
    HealthAgeDialPetalMark(
        size = size,
        fills = fills,
        tabShape = shape == HealthAgeDialPetalShape.Tab,
        modifier = modifier,
    )
}

private fun DrawScope.drawHeartGlyph(color: Color) {
    val sx = size.width / 24f
    val sy = size.height / 24f
    fun point(x: Float, y: Float): Offset {
        return Offset(x * sx, y * sy)
    }
    val path = Path()
    path.moveTo(point(12f, 21.2f).x, point(12f, 21.2f).y)
    path.cubicTo(
        point(11.7f, 21.2f).x,
        point(11.7f, 21.2f).y,
        point(11.4f, 21.1f).x,
        point(11.4f, 21.1f).y,
        point(11.2f, 20.9f).x,
        point(11.2f, 20.9f).y,
    )
    path.cubicTo(
        point(6.2f, 16.8f).x,
        point(6.2f, 16.8f).y,
        point(2.2f, 13.4f).x,
        point(2.2f, 13.4f).y,
        point(2.2f, 8.9f).x,
        point(2.2f, 8.9f).y,
    )
    path.cubicTo(
        point(2.2f, 5.9f).x,
        point(2.2f, 5.9f).y,
        point(4.5f, 3.6f).x,
        point(4.5f, 3.6f).y,
        point(7.4f, 3.6f).x,
        point(7.4f, 3.6f).y,
    )
    path.cubicTo(
        point(9.3f, 3.6f).x,
        point(9.3f, 3.6f).y,
        point(10.9f, 4.6f).x,
        point(10.9f, 4.6f).y,
        point(12f, 6.2f).x,
        point(12f, 6.2f).y,
    )
    path.cubicTo(
        point(13.1f, 4.6f).x,
        point(13.1f, 4.6f).y,
        point(14.7f, 3.6f).x,
        point(14.7f, 3.6f).y,
        point(16.6f, 3.6f).x,
        point(16.6f, 3.6f).y,
    )
    path.cubicTo(
        point(19.5f, 3.6f).x,
        point(19.5f, 3.6f).y,
        point(21.8f, 5.9f).x,
        point(21.8f, 5.9f).y,
        point(21.8f, 8.9f).x,
        point(21.8f, 8.9f).y,
    )
    path.cubicTo(
        point(21.8f, 13.4f).x,
        point(21.8f, 13.4f).y,
        point(17.8f, 16.8f).x,
        point(17.8f, 16.8f).y,
        point(12.8f, 20.9f).x,
        point(12.8f, 20.9f).y,
    )
    path.cubicTo(
        point(12.6f, 21.1f).x,
        point(12.6f, 21.1f).y,
        point(12.3f, 21.2f).x,
        point(12.3f, 21.2f).y,
        point(12f, 21.2f).x,
        point(12f, 21.2f).y,
    )
    path.close()
    drawPath(path, color)
}

@Composable
internal fun HealthAgeDialHeartGlyph(
    size: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size.toFloat().dp)) {
        drawHeartGlyph(color)
    }
}

@Composable
internal fun HeartGlyph(
    size: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    HealthAgeDialHeartGlyph(size = size, color = color, modifier = modifier)
}

private fun DrawScope.drawGridGlyph(color: Color) {
    val scale = size.width / 24f
    val cells = listOf(
        Offset(2.5f, 2.5f),
        Offset(12.9f, 2.5f),
        Offset(2.5f, 12.9f),
        Offset(12.9f, 12.9f),
    )
    cells.forEach { cell ->
        drawRoundRect(
            color = color,
            topLeft = Offset(cell.x * scale, cell.y * scale),
            size = Size(8.6f * scale, 8.6f * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale, 2f * scale),
        )
    }
}

@Composable
internal fun HealthAgeDialGridGlyph(
    size: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size.toFloat().dp)) {
        drawGridGlyph(color)
    }
}

@Composable
internal fun GridGlyph(
    size: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    HealthAgeDialGridGlyph(size = size, color = color, modifier = modifier)
}

private fun DrawScope.drawBedGlyph(color: Color) {
    val sx = size.width / 24f
    val sy = size.height / 24f
    fun point(x: Float, y: Float): Offset {
        return Offset(x * sx, y * sy)
    }
    val top = Path()
    top.moveTo(point(3.6f, 6.2f).x, point(3.6f, 6.2f).y)
    top.cubicTo(
        point(3.6f, 5.2f).x,
        point(3.6f, 5.2f).y,
        point(4.4f, 4.4f).x,
        point(4.4f, 4.4f).y,
        point(5.4f, 4.4f).x,
        point(5.4f, 4.4f).y,
    )
    top.lineTo(point(18.6f, 4.4f).x, point(18.6f, 4.4f).y)
    top.cubicTo(
        point(19.6f, 4.4f).x,
        point(19.6f, 4.4f).y,
        point(20.4f, 5.2f).x,
        point(20.4f, 5.2f).y,
        point(20.4f, 6.2f).x,
        point(20.4f, 6.2f).y,
    )
    top.lineTo(point(20.4f, 10.2f).x, point(20.4f, 10.2f).y)
    top.lineTo(point(18.6f, 10.2f).x, point(18.6f, 10.2f).y)
    top.lineTo(point(18.6f, 9.1f).x, point(18.6f, 9.1f).y)
    top.cubicTo(
        point(18.6f, 8.2f).x,
        point(18.6f, 8.2f).y,
        point(17.9f, 7.5f).x,
        point(17.9f, 7.5f).y,
        point(17f, 7.5f).x,
        point(17f, 7.5f).y,
    )
    top.lineTo(point(14.8f, 7.5f).x, point(14.8f, 7.5f).y)
    top.cubicTo(
        point(13.9f, 7.5f).x,
        point(13.9f, 7.5f).y,
        point(13.2f, 8.2f).x,
        point(13.2f, 8.2f).y,
        point(13.2f, 9.1f).x,
        point(13.2f, 9.1f).y,
    )
    top.lineTo(point(13.2f, 10.2f).x, point(13.2f, 10.2f).y)
    top.lineTo(point(10.8f, 10.2f).x, point(10.8f, 10.2f).y)
    top.lineTo(point(10.8f, 9.1f).x, point(10.8f, 9.1f).y)
    top.cubicTo(
        point(10.8f, 8.2f).x,
        point(10.8f, 8.2f).y,
        point(10.1f, 7.5f).x,
        point(10.1f, 7.5f).y,
        point(9.2f, 7.5f).x,
        point(9.2f, 7.5f).y,
    )
    top.lineTo(point(7f, 7.5f).x, point(7f, 7.5f).y)
    top.cubicTo(
        point(6.1f, 7.5f).x,
        point(6.1f, 7.5f).y,
        point(5.4f, 8.2f).x,
        point(5.4f, 8.2f).y,
        point(5.4f, 9.1f).x,
        point(5.4f, 9.1f).y,
    )
    top.lineTo(point(5.4f, 10.2f).x, point(5.4f, 10.2f).y)
    top.lineTo(point(3.6f, 10.2f).x, point(3.6f, 10.2f).y)
    top.close()
    drawPath(top, color)

    val base = Path()
    base.moveTo(point(2f, 12.6f).x, point(2f, 12.6f).y)
    base.cubicTo(
        point(2f, 11.7f).x,
        point(2f, 11.7f).y,
        point(2.7f, 11f).x,
        point(2.7f, 11f).y,
        point(3.6f, 11f).x,
        point(3.6f, 11f).y,
    )
    base.lineTo(point(20.4f, 11f).x, point(20.4f, 11f).y)
    base.cubicTo(
        point(21.3f, 11f).x,
        point(21.3f, 11f).y,
        point(22f, 11.7f).x,
        point(22f, 11.7f).y,
        point(22f, 12.6f).x,
        point(22f, 12.6f).y,
    )
    base.lineTo(point(22f, 17.2f).x, point(22f, 17.2f).y)
    base.lineTo(point(2f, 17.2f).x, point(2f, 17.2f).y)
    base.close()
    drawPath(base, color)
    drawRoundRect(
        color = color,
        topLeft = point(2f, 17.2f),
        size = Size(2.4f * sx, 2.6f * sy),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.8f * sx, 0.8f * sy),
    )
    drawRoundRect(
        color = color,
        topLeft = point(19.6f, 17.2f),
        size = Size(2.4f * sx, 2.6f * sy),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.8f * sx, 0.8f * sy),
    )
}

@Composable
internal fun HealthAgeDialBedGlyph(
    size: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size.toFloat().dp)) {
        drawBedGlyph(color)
    }
}

@Composable
internal fun BedGlyph(
    size: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    HealthAgeDialBedGlyph(size = size, color = color, modifier = modifier)
}

private fun DrawScope.drawPulseGlyph(color: Color) {
    val sx = size.width / 24f
    val sy = size.height / 24f
    drawRoundRect(
        color = color,
        topLeft = Offset(2f * sx, 4f * sy),
        size = Size(20f * sx, 16f * sy),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * sx, 4f * sy),
    )
    val pulse = Path()
    val points = listOf(
        Offset(4.6f, 12.4f),
        Offset(8.2f, 12.4f),
        Offset(9.7f, 9.8f),
        Offset(11.7f, 15.2f),
        Offset(13.6f, 8.8f),
        Offset(15.2f, 12.4f),
        Offset(19.4f, 12.4f),
    )
    points.forEachIndexed { index, point ->
        val scaled = Offset(point.x * sx, point.y * sy)
        if (index == 0) {
            pulse.moveTo(scaled.x, scaled.y)
        } else {
            pulse.lineTo(scaled.x, scaled.y)
        }
    }
    drawPath(
        path = pulse,
        color = HealthAgeDialColors.white,
        style = Stroke(width = 1.9f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

@Composable
internal fun HealthAgeDialPulseGlyph(
    size: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size.toFloat().dp)) {
        drawPulseGlyph(color)
    }
}

@Composable
internal fun PulseGlyph(
    size: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    HealthAgeDialPulseGlyph(size = size, color = color, modifier = modifier)
}

private fun DrawScope.drawInfoGlyph() {
    val sizeValue = size.width
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(HealthAgeDialColors.info, radius = size.width / 2f, center = center)
    drawCircle(
        color = HealthAgeDialColors.infoGlyph,
        radius = sizeValue * 0.124f,
        center = Offset(center.x, center.y - sizeValue * 0.224f),
    )
    val scale = sizeValue / 25f
    val path = Path()
    fun point(x: Float, y: Float): Offset {
        return Offset(x * scale, y * scale)
    }
    path.moveTo(point(10.2f, 10f).x, point(10.2f, 10f).y)
    path.lineTo(point(13.7f, 10f).x, point(13.7f, 10f).y)
    path.lineTo(point(13.7f, 16.9f).x, point(13.7f, 16.9f).y)
    path.lineTo(point(15.3f, 16.9f).x, point(15.3f, 16.9f).y)
    path.lineTo(point(15.3f, 18.5f).x, point(15.3f, 18.5f).y)
    path.lineTo(point(9.8f, 18.5f).x, point(9.8f, 18.5f).y)
    path.lineTo(point(9.8f, 16.9f).x, point(9.8f, 16.9f).y)
    path.lineTo(point(11.5f, 16.9f).x, point(11.5f, 16.9f).y)
    path.lineTo(point(11.5f, 11.6f).x, point(11.5f, 11.6f).y)
    path.lineTo(point(10.2f, 11.6f).x, point(10.2f, 11.6f).y)
    path.close()
    drawPath(path, HealthAgeDialColors.infoGlyph)
}

@Composable
internal fun HealthAgeDialInfoGlyph(
    size: Double,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size.toFloat().dp)) {
        drawInfoGlyph()
    }
}

@Composable
internal fun InfoGlyph(
    size: Double,
    modifier: Modifier = Modifier,
) {
    HealthAgeDialInfoGlyph(size = size, modifier = modifier)
}

@Composable
internal fun HealthAgeDialMiniDial(
    size: Double,
    modifier: Modifier = Modifier,
) {
    val dial = HealthAgeDialLayout.Dial
    val radius = ((HealthAgeDialLayout.Chip.ring - 6.0) / 2.0).toFloat()
    val stops = ringStops
        .map { stop ->
            val position = (((stop.angle - 90.0) % 360.0 + 360.0) % 360.0) / 360.0
            position.toFloat() to stop.color
        }
        .sortedBy { it.first }
        .toTypedArray()
    Canvas(modifier = modifier.size(size.toFloat().dp)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val scale = this.size.width / size.toFloat()
        val radiusPx = radius * scale
        val ringBrush = Brush.sweepGradient(*stops)
        drawArc(
            brush = ringBrush,
            startAngle = (dial.start - 90.0).toFloat(),
            sweepAngle = (dial.end - dial.start).toFloat(),
            useCenter = false,
            topLeft = Offset(center.x - radiusPx, center.y - radiusPx),
            size = Size(radiusPx * 2f, radiusPx * 2f),
            style = Stroke(width = 6f * scale, cap = StrokeCap.Round),
            alpha = 0.78f,
        )
        val angle = (REST_YEARS * dial.degreesPerYear - 90.0) * PI / 180.0
        drawCircle(
            color = Color(0xFF4A8FDB),
            radius = 3.3f * scale,
            center = Offset(
                x = center.x + radiusPx * cos(angle).toFloat(),
                y = center.y + radiusPx * sin(angle).toFloat(),
            ),
        )
    }
}
