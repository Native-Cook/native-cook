package com.ngocdevv.nativecook.blocks.progress.healthagedial

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

internal val HEALTH_AGE_DIAL_OUTER: Float = (HealthAgeDialLayout.Dial.radius + HealthAgeDialLayout.Dial.stroke / 2.0).toFloat()
internal val HEALTH_AGE_DIAL_SIZE: Float = (2.0 * (HEALTH_AGE_DIAL_OUTER + HealthAgeDialLayout.Dial.canvasPad)).toFloat()
private const val SF_ASCENT = 0.953f

@Composable
internal fun HealthAgeDialDialLayer(
    frame: () -> HealthAgeDialRenderFrame,
    numberOpacity: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .graphicsLayer { clip = false }
            .then(Modifier),
    ) {
        val current = frame()
        val density = density
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            canvas.save()
            canvas.scale(density, density)
            drawHealthAgeDialRing(canvas, current)
            drawHealthAgeDialNumber(canvas, current.years, numberOpacity())
            canvas.restore()
        }
    }
}

private fun drawHealthAgeDialRing(
    canvas: android.graphics.Canvas,
    frame: HealthAgeDialRenderFrame,
) {
    val dial = HealthAgeDialLayout.Dial
    val radius = dial.radius.toFloat()
    val center = HEALTH_AGE_DIAL_SIZE / 2f
    val ringRect = RectF(
        center - radius,
        center - radius,
        center + radius,
        center + radius,
    )
    val sweep = healthAgeDialSweepGradient(center)
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dial.stroke.toFloat()
        strokeCap = Paint.Cap.ROUND
        shader = sweep
        maskFilter = BlurMaskFilter(dial.edgeSoftness.toFloat(), BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawArc(ringRect, (dial.start - 90.0).toFloat(), (dial.end - dial.start).toFloat(), false, ringPaint)

    val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HealthAgeDialColors.tick.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = dial.tickWidth.toFloat()
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter((dial.edgeSoftness * 0.6).toFloat(), BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawLine(
        center,
        (center - dial.radius - dial.tickLength / 2.0).toFloat(),
        center,
        (center - dial.radius + dial.tickLength / 2.0).toFloat(),
        tickPaint,
    )

    drawHealthAgeDialTrail(canvas, center, frame.years, frame.trail)

    val angle = Math.toRadians(frame.years * dial.degreesPerYear.toDouble() - 90.0)
    val dotX = center + radius * cos(angle).toFloat()
    val dotY = center + radius * sin(angle).toFloat()
    val dotRadius = (dial.dot / 2.0 * (1.0 + 0.14 * frame.lift)).toFloat()
    if (frame.lift > 0.001) {
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = HealthAgeDialColors.blue.copy(alpha = (0.55 * frame.lift).toFloat()).toArgb()
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(dotX, dotY, dotRadius, glowPaint)
    }
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HealthAgeDialColors.blue.toArgb()
    }
    canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)
    val dotBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color(red = 10, green = 100, blue = 190, alpha = 89).toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    canvas.drawCircle(dotX, dotY, dotRadius, dotBorder)
}

private fun healthAgeDialSweepGradient(center: Float): Shader {
    val sorted = ringStops.map { stop ->
        var position = ((stop.angle - 90.0) % 360.0 + 360.0) % 360.0 / 360.0
        if (position < 0.0) {
            position += 1.0
        }
        position.toFloat() to stop.color.toArgb()
    }.sortedBy { it.first }
    val before = ringStops.last { it.angle <= 90.0 }
    val after = ringStops.first { it.angle > 90.0 }
    val seamProgress = ((90.0 - before.angle) / (after.angle - before.angle)).toFloat()
    val seam = mixColor(before.color, after.color, seamProgress).toArgb()
    val colors = IntArray(sorted.size + 2)
    val positions = FloatArray(sorted.size + 2)
    colors[0] = seam
    positions[0] = 0f
    sorted.forEachIndexed { index, item ->
        colors[index + 1] = item.second
        positions[index + 1] = item.first
    }
    colors[colors.lastIndex] = seam
    positions[positions.lastIndex] = 1f
    return SweepGradient(center, center, colors, positions)
}

private fun mixColor(from: Color, to: Color, amount: Float): Color {
    return Color(
        red = from.red + (to.red - from.red) * amount,
        green = from.green + (to.green - from.green) * amount,
        blue = from.blue + (to.blue - from.blue) * amount,
        alpha = from.alpha + (to.alpha - from.alpha) * amount,
    )
}

private fun drawHealthAgeDialTrail(
    canvas: android.graphics.Canvas,
    center: Float,
    years: Double,
    length: Double,
) {
    if (abs(length) <= 0.01) {
        return
    }
    val dial = HealthAgeDialLayout.Dial
    val radius = dial.radius.toFloat()
    val ringRect = RectF(
        center - radius,
        center - radius,
        center + radius,
        center + radius,
    )
    val opacity = HealthAgeDialMotion.clamp01(
        abs(length) / (HealthAgeDialMotion.Trail.maximum * HealthAgeDialMotion.Trail.fade),
    )
    val dotAngle = years * dial.degreesPerYear
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.White.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = (dial.stroke * 0.86).toFloat()
        maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    repeat(18) { index ->
        val from = index / 18.0
        val to = (index + 1) / 18.0
        val angle0 = dotAngle + length * (1.0 - from) - 90.0
        val angle1 = dotAngle + length * (1.0 - to) - 90.0
        paint.alpha = (255.0 * opacity * (0.1 + 0.85 * to)).toInt().coerceIn(0, 255)
        paint.strokeCap = if (index == 17) Paint.Cap.ROUND else Paint.Cap.BUTT
        canvas.drawArc(ringRect, angle0.toFloat(), (angle1 - angle0).toFloat(), false, paint)
    }
    paint.xfermode = null
}

private fun drawHealthAgeDialNumber(
    canvas: android.graphics.Canvas,
    years: Double,
    opacity: Float,
) {
    if (opacity <= 0f) {
        return
    }
    val number = HealthAgeDialType.number
    val center = HEALTH_AGE_DIAL_SIZE / 2f
    val numberCenter = (center + HealthAgeDialLayout.numberCenterX - HealthAgeDialLayout.Dial.cx).toFloat()
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = number.color.copy(alpha = opacity).toArgb()
        textSize = number.size.toFloat()
        typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        letterSpacing = (number.tracking / number.size).toFloat()
        textAlign = Paint.Align.LEFT
    }
    val parts = listOf("-", "-3", "-3.", "-3.9")
    val widths = parts.map(textPaint::measureText)
    val totalWidth = widths[3]
    val originX = numberCenter - totalWidth / 2f
    val lineHeight = round(number.size * 1.2).toFloat()
    val top = (center + HealthAgeDialBaseline.number - HealthAgeDialLayout.Dial.cy - SF_ASCENT * number.size).toFloat()
    val metrics = textPaint.fontMetrics
    val baseline = top + (lineHeight - (metrics.bottom - metrics.top)) / 2f - metrics.top
    val absolute = abs(years) * 10.0
    val rawTenths = absolute - kotlin.math.floor(absolute / 10.0) * 10.0
    val tenths = HealthAgeDialMotion.rollStep(rawTenths)
    val units = HealthAgeDialMotion.rollStep(kotlin.math.floor(absolute / 10.0) + maxOf(0.0, rawTenths - 9.0))

    canvas.save()
    canvas.clipRect(originX - 16f, top, originX + totalWidth + 16f, top + lineHeight)
    drawAlignedText(canvas, "-", originX + widths[0], baseline, textPaint, HealthAgeDialMotion.seg(-years, HealthAgeDialMotion.Sign.from, HealthAgeDialMotion.Sign.to), Paint.Align.RIGHT)
    drawAlignedText(canvas, "+", originX + widths[0], baseline, textPaint, HealthAgeDialMotion.seg(years, HealthAgeDialMotion.Sign.from, HealthAgeDialMotion.Sign.to), Paint.Align.RIGHT)
    drawRollingColumn(canvas, originX + widths[0], widths[1] - widths[0], baseline, lineHeight, units, textPaint)
    drawAlignedText(canvas, ".", originX + (widths[1] + widths[2]) / 2f, baseline, textPaint, 1.0, Paint.Align.CENTER)
    drawRollingColumn(canvas, originX + widths[2], widths[3] - widths[2], baseline, lineHeight, tenths, textPaint)
    canvas.restore()

    val unit = HealthAgeDialType.unit
    val unitPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = unit.color.copy(alpha = opacity).toArgb()
        textSize = unit.size.toFloat()
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        letterSpacing = if (unit.size == 0.0) 0f else (unit.tracking / unit.size).toFloat()
        textAlign = Paint.Align.CENTER
    }
    val unitCenter = (center + HealthAgeDialLayout.unitCenterX - HealthAgeDialLayout.Dial.cx).toFloat()
    val unitTop = (center + HealthAgeDialBaseline.unit - HealthAgeDialLayout.Dial.cy - SF_ASCENT * unit.size).toFloat()
    canvas.drawText(HealthAgeDialCopy.unit, unitCenter, unitTop - unitPaint.fontMetrics.top, unitPaint)
}

private fun drawRollingColumn(
    canvas: android.graphics.Canvas,
    originX: Float,
    width: Float,
    baseline: Float,
    lineHeight: Float,
    position: Double,
    paint: TextPaint,
) {
    for (index in 0..10) {
        val distance = index - position
        val alpha = HealthAgeDialMotion.clamp01(1.0 - abs(distance) * HealthAgeDialMotion.Roll.falloff)
        drawAlignedText(
            canvas = canvas,
            text = (index % 10).toString(),
            x = originX + width / 2f,
            baseline = baseline + (distance * lineHeight * HealthAgeDialMotion.Roll.travel).toFloat(),
            paint = paint,
            opacity = alpha,
            align = Paint.Align.CENTER,
        )
    }
}

private fun drawAlignedText(
    canvas: android.graphics.Canvas,
    text: String,
    x: Float,
    baseline: Float,
    paint: TextPaint,
    opacity: Double,
    align: Paint.Align,
) {
    val previousAlpha = paint.alpha
    val previousAlign = paint.textAlign
    paint.alpha = (previousAlpha * HealthAgeDialMotion.clamp01(opacity)).toInt().coerceIn(0, 255)
    paint.textAlign = align
    canvas.drawText(text, x, baseline, paint)
    paint.alpha = previousAlpha
    paint.textAlign = previousAlign
}
