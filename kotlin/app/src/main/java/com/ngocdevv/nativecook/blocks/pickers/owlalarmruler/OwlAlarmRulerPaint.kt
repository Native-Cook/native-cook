package com.ngocdevv.nativecook.blocks.pickers.owlalarmruler

import android.graphics.Matrix
import android.graphics.Paint as NativePaint
import android.graphics.RectF
import android.text.TextPaint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin

internal fun DrawScope.drawOwlAlarmRuler(
    frame: OwlAlarmRulerMotion.Frame,
    pose: OwlAlarmRulerMotion.Pose,
    geo: OwlAlarmRulerGeo,
    assets: OwlAlarmRulerAssets,
    label: String,
    backScale: Float = 1f,
    still: Boolean = false,
) {
    withTransform({ scale(geo.scale, geo.scale, Offset.Zero) }) {
        drawOwlAlarmRulerContent(
            frame = frame,
            pose = pose,
            geo = geo,
            assets = assets,
            label = label,
            backScale = backScale,
            still = still,
        )
    }
}

private fun DrawScope.drawOwlAlarmRulerContent(
    frame: OwlAlarmRulerMotion.Frame,
    pose: OwlAlarmRulerMotion.Pose,
    geo: OwlAlarmRulerGeo,
    assets: OwlAlarmRulerAssets,
    label: String,
    backScale: Float,
    still: Boolean,
) {
    drawBackdrop(pose, geo)
    drawHeader(geo, backScale)
    drawMascot(pose, geo, assets)
    drawCopy(geo)
    drawClock(frame, geo, still)
    drawRuler(frame.pos, pose.needle, geo)
    drawCaption(geo)
    drawButton(frame, pose, geo, label)
}

private fun DrawScope.drawBackdrop(
    pose: OwlAlarmRulerMotion.Pose,
    geo: OwlAlarmRulerGeo,
) {
    drawRect(
        color = Color(OwlAlarmRulerTheme.colors.page),
        size = Size(geo.width, geo.height),
    )
    val diagonal = OwlAlarmRulerTheme.layout.diagonal
    val reach = geo.width + geo.height
    val y0 = geo.top + diagonal.y0.toFloat()
    val pivot = Offset(
        x = geo.width / 2f + OwlAlarmRulerTheme.layout.sundialPivot.dx.toFloat(),
        y = geo.top + OwlAlarmRulerTheme.layout.sundialPivot.y.toFloat(),
    )
    val band = Path().apply {
        moveTo(-reach, y0 - reach * diagonal.slope.toFloat())
        lineTo(reach, y0 + reach * diagonal.slope.toFloat())
        lineTo(reach, -reach)
        lineTo(-reach, -reach)
        close()
    }
    withTransform({ rotate(pose.sundial.toFloat(), pivot) }) {
        drawPath(band, Color(OwlAlarmRulerTheme.colors.band))
    }
    val shadow = OwlAlarmRulerTheme.layout.groundShadow
    val lift = OwlAlarmRulerMotion.clamp01(pose.lift).toFloat()
    val shrink = 1f - OwlAlarmRulerMotion.STARTLE.shadowScale.toFloat() * lift
    val alpha = 1f - OwlAlarmRulerMotion.STARTLE.shadowFade.toFloat() * lift
    val radiusX = shadow.rx.toFloat() * shrink
    val radiusY = shadow.ry.toFloat() * shrink
    val centerX = geo.width / 2f + shadow.dx.toFloat()
    val centerY = geo.top + shadow.cy.toFloat()
    drawOval(
        color = Color(OwlAlarmRulerTheme.colors.shadow).copy(alpha = alpha),
        topLeft = Offset(centerX - radiusX, centerY - radiusY),
        size = Size(radiusX * 2f, radiusY * 2f),
    )
}

private fun DrawScope.drawHeader(
    geo: OwlAlarmRulerGeo,
    backScale: Float,
) {
    val back = OwlAlarmRulerTheme.layout.back
    val left = OwlAlarmRulerTheme.space.backInset.toFloat()
    val top = geo.top + OwlAlarmRulerTheme.space.headerTop.toFloat()
    val diameter = back.d.toFloat()
    val center = Offset(left + diameter / 2f, top + diameter / 2f)
    withTransform({ scale(backScale, backScale, center) }) {
        drawRoundRect(
            color = Color(OwlAlarmRulerTheme.colors.backFill),
            topLeft = Offset(left, top),
            size = Size(diameter, diameter),
            cornerRadius = CornerRadius(diameter / 2f),
        )
        val arrow = back.arrow
        val arrowPath = Path().apply {
            moveTo(left + arrow.armX.toFloat(), center.y - arrow.armDy.toFloat())
            lineTo(left + arrow.tipX.toFloat(), center.y)
            lineTo(left + arrow.armX.toFloat(), center.y + arrow.armDy.toFloat())
        }
        val arrowStroke = Stroke(
            width = arrow.stroke.toFloat(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawPath(
            path = arrowPath,
            color = Color(OwlAlarmRulerTheme.colors.ink),
            style = arrowStroke,
        )
        drawLine(
            color = Color(OwlAlarmRulerTheme.colors.ink),
            start = Offset(left + arrow.tipX.toFloat(), center.y),
            end = Offset(left + arrow.endX.toFloat(), center.y),
            strokeWidth = arrow.stroke.toFloat(),
            cap = StrokeCap.Round,
        )
    }
    val track = OwlAlarmRulerTheme.layout.track
    val trackLeft = (geo.width - track.w.toFloat()) / 2f
    val trackTop = center.y - track.h.toFloat() / 2f
    drawRoundRect(
        color = Color(OwlAlarmRulerTheme.colors.track),
        topLeft = Offset(trackLeft, trackTop),
        size = Size(track.w.toFloat(), track.h.toFloat()),
        cornerRadius = CornerRadius(track.h.toFloat() / 2f),
    )
    drawRoundRect(
        color = Color(OwlAlarmRulerTheme.colors.ink),
        topLeft = Offset(trackLeft, trackTop),
        size = Size(track.fill.toFloat(), track.h.toFloat()),
        cornerRadius = CornerRadius(track.h.toFloat() / 2f),
    )
}

private fun DrawScope.drawMascot(
    pose: OwlAlarmRulerMotion.Pose,
    geo: OwlAlarmRulerGeo,
    assets: OwlAlarmRulerAssets,
) {
    val mascot = OwlAlarmRulerTheme.layout.mascot
    val width = mascot.w.toFloat()
    val height = mascot.h.toFloat()
    val left = geo.width / 2f + mascot.dx.toFloat() - width / 2f
    val top = geo.top + mascot.y.toFloat()
    val pivot = Offset(
        x = left + width / 2f,
        y = top + height * mascot.pivotY.toFloat(),
    )
    withTransform({
        translate(0f, pose.jumpY.toFloat())
        rotate(pose.lean.toFloat(), pivot)
        scale(pose.sx.toFloat(), pose.sy.toFloat(), pivot)
    }) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(
                assets.mascot,
                null,
                RectF(left, top, left + width, top + height),
                OwlAlarmRulerPaintCache.bitmap,
            )
        }
        OwlAlarmRulerTheme.layout.eyes.forEachIndexed { index, eye ->
            drawEye(
                index = index,
                eye = eye,
                pose = pose,
                left = left,
                top = top,
                assets = assets,
            )
        }
    }
}

private fun DrawScope.drawEye(
    index: Int,
    eye: OwlAlarmRulerTheme.Eye,
    pose: OwlAlarmRulerMotion.Pose,
    left: Float,
    top: Float,
    assets: OwlAlarmRulerAssets,
) {
    val radius = eye.r.toFloat()
    val diameter = radius * 2f
    val eyeLeft = left + eye.cx.toFloat() - radius
    val eyeTop = top + eye.cy.toFloat() - radius
    val eyePath = Path().apply {
        addOval(Rect(eyeLeft, eyeTop, eyeLeft + diameter, eyeTop + diameter))
    }
    clipPath(eyePath) {
        val rest = OwlAlarmRulerTheme.layout.pupil.rest[index]
        val pupilDiameter = OwlAlarmRulerTheme.layout.pupil.d.toFloat()
        val pupilCenter = Offset(
            x = left + eye.cx.toFloat() + rest.x.toFloat() + pose.pupilDX.toFloat(),
            y = top + eye.cy.toFloat() + rest.y.toFloat() + pose.pupilDY.toFloat(),
        )
        val pupilWidth = pupilDiameter * pose.pupilScale.toFloat()
        val pupilRect = RectF(
            pupilCenter.x - pupilWidth / 2f,
            pupilCenter.y - pupilWidth / 2f,
            pupilCenter.x + pupilWidth / 2f,
            pupilCenter.y + pupilWidth / 2f,
        )
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(
                assets.pupil,
                null,
                pupilRect,
                OwlAlarmRulerPaintCache.bitmap,
            )
        }
        val blink = pose.blink.toFloat()
        val lidClose = 0.6f
        val offscreen = diameter + 1f
        val upperY = eyeTop - offscreen + (offscreen - diameter * (1f - lidClose)) * blink
        val lowerY = eyeTop + offscreen - (offscreen - diameter * lidClose) * blink
        drawRect(
            color = Color(OwlAlarmRulerTheme.colors.lid),
            topLeft = Offset(eyeLeft, upperY),
            size = Size(diameter, diameter),
        )
        val lash = OwlAlarmRulerTheme.layout.lash.toFloat() / 3f
        drawRect(
            color = Color(OwlAlarmRulerTheme.colors.lash),
            topLeft = Offset(eyeLeft, upperY + diameter - lash),
            size = Size(diameter, lash),
        )
        drawRect(
            color = Color(OwlAlarmRulerTheme.colors.lid),
            topLeft = Offset(eyeLeft, lowerY),
            size = Size(diameter, diameter),
        )
    }
}

private fun DrawScope.drawCopy(geo: OwlAlarmRulerGeo) {
    val paint = OwlAlarmRulerText.headline
    val metrics = OwlAlarmRulerText.metrics(geo.scale)
    paint.textAlign = NativePaint.Align.CENTER
    val baselineOffset = metrics.headline.baseline
    val topOffset = (OwlAlarmRulerTheme.ASCENT * OwlAlarmRulerTheme.type.headline.size).toFloat()
    drawNativeText(
        value = OwlAlarmRulerTheme.copy.line1,
        x = geo.width / 2f,
        baseline = geo.top + OwlAlarmRulerTheme.layout.headline[0].base.toFloat() - topOffset + baselineOffset,
        paint = paint,
    )
    drawNativeText(
        value = OwlAlarmRulerTheme.copy.line2,
        x = geo.width / 2f,
        baseline = geo.top + OwlAlarmRulerTheme.layout.headline[1].base.toFloat() - topOffset + baselineOffset,
        paint = paint,
    )
}

private fun DrawScope.drawClock(
    frame: OwlAlarmRulerMotion.Frame,
    geo: OwlAlarmRulerGeo,
    still: Boolean,
) {
    val layout = OwlAlarmRulerTheme.layout.time
    val wheels = OwlAlarmRulerMotion.wheels(frame.pos)
    val paint = OwlAlarmRulerText.time
    val metrics = OwlAlarmRulerText.metrics(geo.scale)
    val metric = metrics.time
    val cellHeight = metric.height
    val rowTop = geo.top + layout.base.toFloat() - (OwlAlarmRulerTheme.ASCENT * OwlAlarmRulerTheme.type.time.size).toFloat()
    val hour = wheels.hour
    val hourIndex = floor(hour).toInt()
    val hourFraction = (hour - hourIndex).toFloat()
    val currentHourWidth = metrics.hourWidths[hourIndex % 12]
    val nextHourWidth = metrics.hourWidths[(hourIndex + 1) % 12]
    val visibleHourWidth = currentHourWidth + (nextHourWidth - currentHourWidth) * hourFraction
    val maxHourWidth = metrics.maxHourWidth
    val digitWidth = metrics.digitWidth
    val colonWidth = metrics.colonWidth
    val totalWidth = maxHourWidth +
        layout.colonGap.toFloat() +
        colonWidth +
        layout.tight.toFloat() * 2f +
        digitWidth * 2f
    val rowCenter = geo.width / 2f + layout.dx.toFloat()
    val centering = -(maxHourWidth - visibleHourWidth) / 2f
    var cursor = rowCenter - totalWidth / 2f + centering
    drawWheel(
        cells = OwlAlarmRulerText.hours,
        value = hour,
        left = cursor,
        top = rowTop,
        width = maxHourWidth,
        cellHeight = cellHeight,
        baseline = metric.baseline,
        align = NativePaint.Align.RIGHT,
        paint = paint,
    )
    cursor += maxHourWidth + layout.colonGap.toFloat()
    paint.textAlign = NativePaint.Align.LEFT
    drawNativeText(
        value = ":",
        x = cursor,
        baseline = rowTop + metric.baseline - layout.colonRise.toFloat(),
        paint = paint,
    )
    cursor += colonWidth + layout.tight.toFloat()
    drawWheel(
        cells = OwlAlarmRulerText.tens,
        value = wheels.tens,
        left = cursor,
        top = rowTop,
        width = digitWidth,
        cellHeight = cellHeight,
        baseline = metric.baseline,
        align = NativePaint.Align.CENTER,
        paint = OwlAlarmRulerText.timeTabular,
    )
    cursor += digitWidth + layout.tight.toFloat()
    drawWheel(
        cells = OwlAlarmRulerText.ones,
        value = wheels.ones,
        left = cursor,
        top = rowTop,
        width = digitWidth,
        cellHeight = cellHeight,
        baseline = metric.baseline,
        align = NativePaint.Align.CENTER,
        paint = OwlAlarmRulerText.timeTabular,
    )
    drawMeridiem(frame.flip, geo, still)
}

private fun DrawScope.drawWheel(
    cells: Array<String>,
    value: Double,
    left: Float,
    top: Float,
    width: Float,
    cellHeight: Float,
    baseline: Float,
    align: NativePaint.Align,
    paint: TextPaint,
) {
    val index = floor(value).toInt()
    val fraction = (value - index).toFloat()
    val textX = when (align) {
        NativePaint.Align.RIGHT -> left + width
        NativePaint.Align.CENTER -> left + width / 2f
        NativePaint.Align.LEFT -> left
    }
    paint.textAlign = align
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        nativeCanvas.save()
        nativeCanvas.clipRect(left, top, left + width, top + cellHeight)
        nativeCanvas.drawText(
            cells[index % (cells.size - 1)],
            textX,
            top + baseline - fraction * cellHeight,
            paint,
        )
        nativeCanvas.drawText(
            cells[index % (cells.size - 1) + 1],
            textX,
            top + baseline + (1f - fraction) * cellHeight,
            paint,
        )
        nativeCanvas.restore()
    }
}

private fun DrawScope.drawMeridiem(
    flip: Double,
    geo: OwlAlarmRulerGeo,
    still: Boolean,
) {
    val paint = OwlAlarmRulerText.meridiem
    val metric = OwlAlarmRulerText.metrics(geo.scale).meridiem
    val top = geo.top +
        OwlAlarmRulerTheme.layout.meridiem.base.toFloat() -
        (OwlAlarmRulerTheme.ASCENT * OwlAlarmRulerTheme.type.meridiem.size).toFloat()
    val centerX = geo.width / 2f + OwlAlarmRulerTheme.layout.meridiem.dx.toFloat()
    val centerY = top + metric.height / 2f
    val cosine = cos(flip * Math.PI / 180.0).toFloat()
    paint.textAlign = NativePaint.Align.CENTER
    if (still) {
        drawMeridiemFace("PM", 0f, cosine.coerceAtLeast(0f), centerX, centerY, top, metric.baseline, paint)
        drawMeridiemFace("AM", 0f, (-cosine).coerceAtLeast(0f), centerX, centerY, top, metric.baseline, paint)
        return
    }
    if (cosine > 0f) {
        drawMeridiemFace("PM", (-flip).toFloat(), 1f, centerX, centerY, top, metric.baseline, paint)
    }
    if (cosine < 0f) {
        drawMeridiemFace("AM", (180.0 - flip).toFloat(), 1f, centerX, centerY, top, metric.baseline, paint)
    }
}

private fun DrawScope.drawMeridiemFace(
    value: String,
    angle: Float,
    alpha: Float,
    centerX: Float,
    centerY: Float,
    top: Float,
    baseline: Float,
    paint: TextPaint,
) {
    if (alpha <= 0f) {
        return
    }
    paint.alpha = jsRound(alpha * 255f).coerceIn(0, 255)
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        val matrix = OwlAlarmRulerPaintCache.matrix
        val values = OwlAlarmRulerPaintCache.matrixValues
        val radians = angle * Math.PI.toFloat() / 180f
        values[Matrix.MSCALE_X] = 1f
        values[Matrix.MSKEW_X] = 0f
        values[Matrix.MTRANS_X] = 0f
        values[Matrix.MSKEW_Y] = 0f
        values[Matrix.MSCALE_Y] = cos(radians)
        values[Matrix.MTRANS_Y] = 0f
        values[Matrix.MPERSP_0] = 0f
        values[Matrix.MPERSP_1] = -sin(radians) / 260f
        values[Matrix.MPERSP_2] = 1f
        matrix.setValues(values)
        matrix.preTranslate(-centerX, -centerY)
        matrix.postTranslate(centerX, centerY)
        nativeCanvas.save()
        nativeCanvas.concat(matrix)
        nativeCanvas.drawText(value, centerX, top + baseline, paint)
        nativeCanvas.restore()
    }
    paint.alpha = 255
}

private fun DrawScope.drawRuler(
    position: Double,
    bend: Double,
    geo: OwlAlarmRulerGeo,
) {
    val ruler = OwlAlarmRulerTheme.layout.ruler
    val centerX = geo.width / 2f
    val base = geo.top + ruler.base.toFloat()
    val limit = geo.width / 2f -
        OwlAlarmRulerTheme.space.screenInset.toFloat() -
        ruler.tickW.toFloat() / 2f +
        0.01f
    val reach = ceil(limit / ruler.spacing.toFloat() + ruler.fade).toInt() + 1
    val nearest = jsRound(position)
    for (tick in nearest - reach..nearest + reach) {
        val distance = tick.toDouble() - position
        val x = centerX + (distance * ruler.spacing).toFloat()
        val over = (abs(x - centerX) - limit) / (ruler.fade * ruler.spacing).toFloat()
        val alpha = 1f - OwlAlarmRulerMotion.clamp01(over.toDouble()).toFloat()
        if (alpha <= 0f) {
            continue
        }
        val shade = OwlAlarmRulerMotion.tickShade(distance)
        val dark = OwlAlarmRulerTheme.colors.tickDark
        val light = OwlAlarmRulerTheme.colors.tickLight
        val red = jsRound(lerpChannel(dark shr 16, light shr 16, shade)).coerceIn(0, 255)
        val green = jsRound(lerpChannel(dark shr 8, light shr 8, shade)).coerceIn(0, 255)
        val blue = jsRound(lerpChannel(dark, light, shade)).coerceIn(0, 255)
        val color = Color(android.graphics.Color.rgb(red, green, blue))
        val roundedAlpha = round(alpha * 10_000f) / 10_000f
        val height = OwlAlarmRulerMotion.tickHeight(distance).toFloat()
        drawRoundRect(
            color = color,
            topLeft = Offset(x - ruler.tickW.toFloat() / 2f, base - height),
            size = Size(ruler.tickW.toFloat(), height),
            cornerRadius = CornerRadius(ruler.tickW.toFloat() / 2f),
            alpha = roundedAlpha,
        )
    }
    val needle = ruler.needle
    val foot = geo.top + needle.cy.toFloat() + needle.h.toFloat() / 2f
    withTransform({ rotate(bend.toFloat(), Offset(centerX, foot)) }) {
        drawRoundRect(
            color = Color(OwlAlarmRulerTheme.colors.needle),
            topLeft = Offset(centerX - needle.w.toFloat() / 2f, foot - needle.h.toFloat()),
            size = Size(needle.w.toFloat(), needle.h.toFloat()),
            cornerRadius = CornerRadius(needle.w.toFloat() / 2f),
        )
    }
}

private fun DrawScope.drawCaption(geo: OwlAlarmRulerGeo) {
    val paint = OwlAlarmRulerText.caption
    val metric = OwlAlarmRulerText.metrics(geo.scale).caption
    val top = geo.top +
        OwlAlarmRulerTheme.layout.caption.base.toFloat() -
        (OwlAlarmRulerTheme.ASCENT * OwlAlarmRulerTheme.type.caption.size).toFloat()
    paint.textAlign = NativePaint.Align.CENTER
    drawNativeText(
        value = OwlAlarmRulerTheme.copy.caption,
        x = geo.width / 2f,
        baseline = top + metric.baseline,
        paint = paint,
    )
}

private fun DrawScope.drawButton(
    frame: OwlAlarmRulerMotion.Frame,
    pose: OwlAlarmRulerMotion.Pose,
    geo: OwlAlarmRulerGeo,
    label: String,
) {
    val buttonHeight = OwlAlarmRulerTheme.layout.cta.h.toFloat()
    val buttonLeft = OwlAlarmRulerTheme.space.screenInset.toFloat()
    val buttonTop = geo.height - geo.bottom - OwlAlarmRulerTheme.space.ctaBottom.toFloat() - buttonHeight
    val buttonWidth = geo.width - buttonLeft * 2f
    val center = Offset(buttonLeft + buttonWidth / 2f, buttonTop + buttonHeight / 2f)
    val pressScale = (1.0 - (1.0 - OwlAlarmRulerMotion.PRESS.scale) * frame.press).toFloat()
    withTransform({ scale(pressScale, pressScale, center) }) {
        drawRoundRect(
            color = Color(OwlAlarmRulerTheme.colors.cta),
            topLeft = Offset(buttonLeft, buttonTop),
            size = Size(buttonWidth, buttonHeight),
            cornerRadius = CornerRadius(OwlAlarmRulerTheme.radius.cta.toFloat()),
        )
        val labelTop = buttonTop +
            OwlAlarmRulerTheme.layout.cta.base.toFloat() -
            (OwlAlarmRulerTheme.ASCENT * OwlAlarmRulerTheme.type.cta.size).toFloat()
        val paint = OwlAlarmRulerText.cta
        paint.textAlign = NativePaint.Align.LEFT
        drawNativeText(
            value = "${OwlAlarmRulerTheme.copy.cta} $label",
            x = buttonLeft + OwlAlarmRulerTheme.space.ctaPadStart.toFloat(),
            baseline = labelTop + OwlAlarmRulerText.metrics(geo.scale).cta.baseline,
            paint = paint,
        )
        val arrow = OwlAlarmRulerTheme.layout.ctaArrow
        val arrowWidth = arrow.shaft.toFloat() + arrow.stroke.toFloat() / 2f
        val totalArrowWidth = arrowWidth + arrow.headW.toFloat()
        val arrowHeight = arrow.headH.toFloat()
        val arrowLeft = buttonLeft +
            buttonWidth -
            OwlAlarmRulerTheme.space.ctaPadEnd.toFloat() -
            totalArrowWidth +
            pose.arrow.toFloat()
        val arrowTop = buttonTop + (buttonHeight - arrowHeight) / 2f
        val arrowY = arrowTop + arrowHeight / 2f
        drawLine(
            color = Color(OwlAlarmRulerTheme.colors.white),
            start = Offset(arrowLeft + arrow.stroke.toFloat() / 2f, arrowY),
            end = Offset(arrowLeft + arrowWidth + arrow.headW.toFloat() * 0.4f, arrowY),
            strokeWidth = arrow.stroke.toFloat(),
            cap = StrokeCap.Round,
        )
        val arrowHead = Path().apply {
            moveTo(arrowLeft + arrowWidth + arrow.headR.toFloat(), arrowTop + arrow.headR.toFloat())
            lineTo(arrowLeft + arrowWidth + arrow.headW.toFloat() - arrow.headR.toFloat(), arrowY)
            lineTo(arrowLeft + arrowWidth + arrow.headR.toFloat(), arrowTop + arrowHeight - arrow.headR.toFloat())
            close()
        }
        drawPath(
            path = arrowHead,
            color = Color(OwlAlarmRulerTheme.colors.white),
        )
        drawPath(
            path = arrowHead,
            color = Color(OwlAlarmRulerTheme.colors.white),
            style = Stroke(
                width = 2f * arrow.headR.toFloat(),
                join = StrokeJoin.Round,
            ),
        )
    }
}

private fun DrawScope.drawNativeText(
    value: String,
    x: Float,
    baseline: Float,
    paint: TextPaint,
) {
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(value, x, baseline, paint)
    }
}

private fun lerpChannel(
    from: Int,
    to: Int,
    amount: Double,
): Double {
    val start = from and 0xFF
    val end = to and 0xFF
    return start + (end - start) * amount
}

private fun jsRound(value: Double): Int = floor(value + 0.5).toInt()

private fun jsRound(value: Float): Int = floor(value.toDouble() + 0.5).toInt()
