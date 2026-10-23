package com.ngocdevv.nativecook.blocks.forms.dialslider.dial

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.createCircularArc
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.getBipolarProgress

/**
 * Bipolar progress ring: positive values sweep clockwise from 12 o'clock,
 * negative values sweep counter-clockwise.
 */
@Composable
internal fun PresetRing(
    value: Double,
    minValue: Double,
    maxValue: Double,
    color: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val progress = getBipolarProgress(value, minValue, maxValue)
    val direction = if (value < 0) -1 else 1
    val arc = createCircularArc(RING_RADIUS, progress, direction)

    Canvas(
        modifier = modifier
            .clearAndSetSemantics {}
            .let { it },
    ) {
        val scale = size.minDimension / DialConfig.ITEM_SIZE.toFloat()
        val center = (DialConfig.ITEM_SIZE / 2).toFloat() * scale
        val radius = RING_RADIUS.toFloat() * scale

        drawCircle(
            color = backgroundColor,
            radius = radius,
            center = Offset(center, center),
            style = Stroke(width = DialConfig.RING_BG_STROKE_WIDTH.dp.toPx()),
        )
        if (arc != null) {
            drawArc(
                color = color,
                startAngle = arc.startAngleDegrees.toFloat(),
                sweepAngle = arc.sweepAngleDegrees.toFloat(),
                useCenter = false,
                topLeft = Offset(center - radius, center - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(
                    width = DialConfig.RING_STROKE_WIDTH.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
        }
    }
}

private val RING_RADIUS: Double = DialConfig.ITEM_SIZE / 2 - DialConfig.RING_STROKE_WIDTH / 2
