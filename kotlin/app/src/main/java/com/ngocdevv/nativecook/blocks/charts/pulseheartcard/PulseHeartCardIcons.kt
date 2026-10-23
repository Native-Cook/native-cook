package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.ngocdevv.nativecook.blocks.charts.pulseheartcard.PulseHeartCardTheme.Colors

/** The pin's small marks, drawn in code (the SVG icons of PulseHeartCard.tsx). [size] is in dp. */
@Composable
internal fun PulseHeartCardHeartIcon(size: Float, color: Color = Colors.heart, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size.dp)) {
        drawPath(pulseHeartCardHeartPath(this.size.width / 24f), color)
    }
}

/** Three activity rings. [scale] is dp per ref pt. */
@Composable
internal fun PulseHeartCardFitnessIcon(scale: Float) {
    Canvas(Modifier.size((PulseHeartCardTheme.Layout.CHIP_ICON * scale).dp)) {
        val unit = scale * density
        val centre = Offset(size.width / 2, size.height / 2)
        val rings = listOf(
            Triple(12.8f, 0.82f, Colors.ringRed),
            Triple(9f, 0.92f, Colors.ringYellow),
            Triple(5.2f, 0.88f, Colors.metricGreen),
        )
        val stroke = Stroke(width = 3.3f * unit, cap = StrokeCap.Round)
        for ((radius, _, color) in rings) {
            drawCircle(color.copy(alpha = 0.18f), radius = radius * unit, center = centre, style = stroke)
        }
        for ((radius, progress, color) in rings) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(centre.x - radius * unit, centre.y - radius * unit),
                size = Size(radius * 2 * unit, radius * 2 * unit),
                style = stroke,
            )
        }
    }
}

/** Four rings on a diamond. */
@Composable
internal fun PulseHeartCardVitalsIcon(scale: Float) {
    Canvas(Modifier.size((PulseHeartCardTheme.Layout.CHIP_ICON * scale).dp)) {
        val unit = scale * density
        val c = Offset(size.width / 2, size.height / 2)
        val points = listOf(
            Offset(c.x, c.y - 8 * unit),
            Offset(c.x - 8 * unit, c.y),
            Offset(c.x + 8 * unit, c.y),
            Offset(c.x, c.y + 8 * unit),
        )
        points.forEachIndexed { index, point ->
            drawCircle(
                color = if (index == 0) Colors.metricPink else Colors.metricBlue,
                radius = 5 * unit,
                center = point,
                style = Stroke(width = 3 * unit),
            )
        }
    }
}

@Composable
internal fun PulseHeartCardChevron(scale: Float, color: Color = Colors.faint) {
    Canvas(Modifier.size((15 * scale).dp, (18 * scale).dp)) {
        val unit = scale * density
        val path = Path().apply {
            moveTo(5 * unit, 3 * unit)
            lineTo(10 * unit, 9 * unit)
            lineTo(5 * unit, 15 * unit)
        }
        drawPath(path, color, style = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The pin's Longevity mark: a dark aperture of eight petals around a small hole. */
@Composable
internal fun PulseHeartCardApertureIcon(size: Float, color: Color) {
    Canvas(Modifier.size(size.dp)) {
        val s = this.size.width / 24f
        drawCircle(color, radius = 10.4f * s, center = Offset(12 * s, 12 * s))
        for (index in 0 until 8) {
            val angle = index * PI / 4 + PI / 8
            drawLine(
                color = Colors.card,
                start = Offset(12 * s, 12 * s),
                end = Offset((12 + 11 * cos(angle)).toFloat() * s, (12 + 11 * sin(angle)).toFloat() * s),
                strokeWidth = 1.7f * s,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(Colors.card, radius = 4 * s, center = Offset(12 * s, 12 * s))
    }
}

@Composable
internal fun PulseHeartCardBrowseIcon(size: Float, color: Color) {
    Canvas(Modifier.size(size.dp)) {
        val s = this.size.width / 24f
        for (row in 0..1) {
            for (column in 0..1) {
                drawSquare(color, s, column, row)
            }
        }
    }
}

private fun DrawScope.drawSquare(color: Color, s: Float, column: Int, row: Int) {
    drawRoundRect(
        color = color,
        topLeft = Offset((1.5f + column * 11) * s, (1.5f + row * 11) * s),
        size = Size(10 * s, 10 * s),
        cornerRadius = CornerRadius(2.3f * s),
    )
}
