package com.ngocdevv.nativecook.blocks.progress.healthagedial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

internal const val HEALTH_AGE_DIAL_CHART_W: Float = 340f
internal const val HEALTH_AGE_DIAL_CHART_H: Float = 124.67f
internal const val CHART_W: Float = HEALTH_AGE_DIAL_CHART_W
internal const val CHART_H: Float = HEALTH_AGE_DIAL_CHART_H

private val HealthAgeDialChartDash = PathEffect.dashPathEffect(floatArrayOf(1.6f, 3.4f))

private fun healthAgeDialLineYAt(
    x: Float,
    points: List<HealthAgeDialPoint>,
): Float {
    val first = points.firstOrNull() ?: return 0f
    val last = points.lastOrNull() ?: return 0f
    if (x <= first.x.toFloat()) {
        return first.y.toFloat()
    }
    for (index in 1 until points.size) {
        val next = points[index]
        if (x <= next.x.toFloat()) {
            val previous = points[index - 1]
            return previous.y.toFloat() +
                (next.y - previous.y).toFloat() * (x - previous.x.toFloat()) /
                (next.x - previous.x).toFloat()
        }
    }
    return last.y.toFloat()
}

@Composable
internal fun HealthAgeDialHistoryChart(
    progress: Double,
    opacity: Double,
    modifier: Modifier = Modifier,
) {
    HealthAgeDialHistoryChart(
        progress = progress.toFloat(),
        opacity = opacity.toFloat(),
        modifier = modifier,
    )
}

@Composable
internal fun HealthAgeDialHistoryChart(
    progress: Float,
    opacity: Float,
    modifier: Modifier = Modifier,
) {
    val chart = HealthAgeDialLayout.chart
    val gridWidth = (chart.right - chart.left).toFloat()
    val gridHeight = (chart.bottom - chart.top).toFloat()
    val baseYValue = chart.base - chart.top
    val baseY = baseYValue.toFloat()
    val points = remember {
        historyPoints.map { point ->
            HealthAgeDialPoint(point.x, baseYValue + point.y)
        }
    }
    val last = points.lastOrNull() ?: return
    val lowest = remember(points) {
        points.maxOfOrNull { point -> point.y.toFloat() } ?: baseY
    }
    val line = remember(points) {
        Path().apply {
            points.forEachIndexed { index, point ->
                if (index == 0) {
                    moveTo(point.x.toFloat(), point.y.toFloat())
                } else {
                    lineTo(point.x.toFloat(), point.y.toFloat())
                }
            }
        }
    }
    val area = remember(line, last, baseY) {
        Path().apply {
            addPath(line)
            lineTo(last.x.toFloat(), baseY)
            lineTo(0f, baseY)
            close()
        }
    }
    Canvas(modifier = modifier.size(CHART_W.dp, CHART_H.dp)) {
        scale(scaleX = density, scaleY = density, pivot = Offset.Zero) {
            drawHealthAgeDialHistory(
                progress = progress,
                opacity = opacity,
                points = points,
                line = line,
                area = area,
                last = last,
                lowest = lowest,
                gridWidth = gridWidth,
                gridHeight = gridHeight,
                baseY = baseY,
            )
        }
    }
}

@Composable
internal fun HealthAgeDialHistoryChart(
    frame: () -> HealthAgeDialRenderFrame,
    modifier: Modifier = Modifier,
) {
    val chart = HealthAgeDialLayout.chart
    val gridWidth = (chart.right - chart.left).toFloat()
    val gridHeight = (chart.bottom - chart.top).toFloat()
    val baseYValue = chart.base - chart.top
    val baseY = baseYValue.toFloat()
    val points = remember {
        historyPoints.map { point -> HealthAgeDialPoint(point.x, baseYValue + point.y) }
    }
    val last = points.lastOrNull() ?: return
    val lowest = remember(points) { points.maxOfOrNull { point -> point.y.toFloat() } ?: baseY }
    val line = remember(points) {
        Path().apply {
            points.forEachIndexed { index, point ->
                if (index == 0) moveTo(point.x.toFloat(), point.y.toFloat())
                else lineTo(point.x.toFloat(), point.y.toFloat())
            }
        }
    }
    val area = remember(line, last, baseY) {
        Path().apply {
            addPath(line)
            lineTo(last.x.toFloat(), baseY)
            lineTo(0f, baseY)
            close()
        }
    }
    Canvas(modifier = modifier.size(CHART_W.dp, CHART_H.dp)) {
        val current = frame()
        scale(scaleX = density, scaleY = density, pivot = Offset.Zero) {
            drawHealthAgeDialHistory(
                progress = current.lineProgress.toFloat(),
                opacity = current.revealOpacity.toFloat(),
                points = points,
                line = line,
                area = area,
                last = last,
                lowest = lowest,
                gridWidth = gridWidth,
                gridHeight = gridHeight,
                baseY = baseY,
            )
        }
    }
}

private fun DrawScope.drawHealthAgeDialHistory(
    progress: Float,
    opacity: Float,
    points: List<HealthAgeDialPoint>,
    line: Path,
    area: Path,
    last: HealthAgeDialPoint,
    lowest: Float,
    gridWidth: Float,
    gridHeight: Float,
    baseY: Float,
) {
    val chart = HealthAgeDialLayout.chart
    val clampedProgress = HealthAgeDialMotion.clamp01(progress.toDouble()).toFloat()
    val clampedOpacity = HealthAgeDialMotion.clamp01(opacity.toDouble()).toFloat()
    val headX = clampedProgress * last.x.toFloat()
    val dotY = healthAgeDialLineYAt(headX, points)
    val dotOpacity = HealthAgeDialMotion.clamp01((clampedProgress / 0.06f).toDouble()).toFloat() * clampedOpacity
    for (column in 0..chart.columns) {
        val x = gridWidth * column / chart.columns + 0.5f
        drawLine(HealthAgeDialColors.grid, Offset(x, 0f), Offset(x, gridHeight), 1f, pathEffect = HealthAgeDialChartDash)
    }
    drawLine(HealthAgeDialColors.grid, Offset(0f, 0.5f), Offset(gridWidth, 0.5f), 1f, pathEffect = HealthAgeDialChartDash)
    drawLine(HealthAgeDialColors.grid, Offset(0f, gridHeight), Offset(gridWidth, gridHeight), 1f)
    if (clampedProgress > 0f && clampedOpacity > 0f) {
        val clipWidth = headX + 4f + if (clampedProgress >= 1f) 8f else 0f
        clipRect(left = -4f, top = 0f, right = clipWidth, bottom = CHART_H) {
            drawPath(
                area,
                Brush.verticalGradient(
                    listOf(HealthAgeDialColors.fillTop, HealthAgeDialColors.fillBottom),
                    startY = baseY,
                    endY = lowest,
                ),
                alpha = clampedOpacity,
            )
            drawPath(
                line,
                Brush.horizontalGradient(
                    listOf(HealthAgeDialColors.line, HealthAgeDialColors.lineEnd),
                    startX = 0f,
                    endX = last.x.toFloat(),
                ),
                alpha = clampedOpacity,
                style = Stroke(chart.lineWidth.toFloat(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
    drawLine(HealthAgeDialColors.baseline, Offset(0f, baseY), Offset(CHART_W, baseY), 1.3f)
    if (dotOpacity > 0f) {
        drawCircle(HealthAgeDialColors.white, (chart.dotRadius + chart.dotRing).toFloat(), Offset(headX, dotY), dotOpacity)
        drawCircle(HealthAgeDialColors.chartDot, chart.dotRadius.toFloat(), Offset(headX, dotY), dotOpacity)
    }
}

@Composable
internal fun HistoryChart(
    progress: Double,
    opacity: Double,
    modifier: Modifier = Modifier,
) {
    HealthAgeDialHistoryChart(progress = progress, opacity = opacity, modifier = modifier)
}
