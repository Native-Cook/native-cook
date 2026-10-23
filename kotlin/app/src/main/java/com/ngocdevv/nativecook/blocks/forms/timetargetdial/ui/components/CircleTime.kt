package com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.AppColors
import com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme.LabelFont
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val Radius = 200.dp
private const val HOUR_LABELS_AMOUNT = 10
private val MaxLineHeight = 20.dp
private val MinLineHeight = 8.dp
private const val LINES_AMOUNT = 200
private val LineWidth = 1.5.dp

private val LabelWidth = 60.dp
private val LabelHeight = 30.dp
private val LabelRadiusOffset = 40.dp

/** The RN container is shifted `right: 60`, so the circle centre sits 60dp left of the screen edge. */
private val CenterXOffset = (-60).dp

/**
 * `src/components/circle-time/index.tsx` + `line-time.tsx`
 *
 * The RN version renders 200 absolutely-positioned `Animated.View` ticks plus 10 hour labels and
 * rotates them with a shared value. Here everything is drawn in a single [Canvas] (only the draw
 * phase re-runs when [selectedDuration] changes) and the same gradient overlays are layered on top.
 */
@Composable
fun CircleTime(
    selectedDuration: () -> Float,
    modifier: Modifier = Modifier,
    bigLineIndexOffset: Int = 10,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(
            color = AppColors.Label,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LabelFont,
        )
    }
    val hourLabels = remember(textMeasurer, labelStyle, bigLineIndexOffset) {
        (0 until HOUR_LABELS_AMOUNT).map { hourIndex ->
            val tickIndex = (hourIndex + 1) * bigLineIndexOffset
            tickIndex to textMeasurer.measure("${hourIndex + 1} hr", labelStyle)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val radius = Radius.toPx()
        val center = Offset(CenterXOffset.toPx(), size.height / 2f)
        val maxLineHeight = MaxLineHeight.toPx()
        val minLineHeight = MinLineHeight.toPx()
        val mediumLineHeight = (maxLineHeight + minLineHeight) / 2f
        val strokeWidth = LineWidth.toPx()

        // interpolate(-progress, [0, circumference], [0, 2π]) collapses to targetIndex * 2π / linesAmount
        val clampedDuration = max(0f, selectedDuration())
        val targetIndex = min((LINES_AMOUNT - 1).toFloat(), clampedDuration * bigLineIndexOffset)
        val progressRadians = targetIndex * (2.0 * PI / LINES_AMOUNT)

        fun angleFor(index: Int): Double = (2.0 * PI / LINES_AMOUNT) * index - progressRadians

        // --- ticks (LineTime) ---
        val midpointOffset = bigLineIndexOffset / 2
        for (index in 0 until LINES_AMOUNT) {
            val isBigLine = index % bigLineIndexOffset == 0
            val isMediumLine = !isBigLine && index % bigLineIndexOffset == midpointOffset
            val height = when {
                isBigLine -> maxLineHeight
                isMediumLine -> mediumLineHeight
                else -> minLineHeight
            }
            val angle = angleFor(index)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()

            // Each tick originates at the circle and points outward.
            drawLine(
                color = AppColors.Tick,
                start = Offset(center.x + dx * radius, center.y + dy * radius),
                end = Offset(center.x + dx * (radius + height), center.y + dy * (radius + height)),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Butt,
            )
        }

        // --- hour labels ---
        val labelRadius = radius + LabelRadiusOffset.toPx()
        val labelLeftOffset = 10.dp.toPx() // `left: 10`
        hourLabels.forEach { (tickIndex, layout) ->
            if (tickIndex > LINES_AMOUNT) return@forEach
            val angle = angleFor(tickIndex)
            val cx = center.x + cos(angle).toFloat() * labelRadius + labelLeftOffset
            val cy = center.y + sin(angle).toFloat() * labelRadius
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f),
            )
        }

        // --- outer mask: horizontal fade that dims the ring towards the right edge ---
        val gradientRadius = radius + 94.dp.toPx() / 2f
        val gradientCenter = Offset(center.x + 27.dp.toPx(), center.y)
        val gradientLeft = gradientCenter.x - gradientRadius
        val gradientSpan = gradientRadius * 2f * 1.1f // end={{ x: 1.1 }}
        // A rectangular mask fades every glyph uniformly; a circular edge can split labels.
        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Black,
                    0.2f to Color.Black,
                    0.4f to Color.Black,
                    0.6f to Color.Black.copy(alpha = 0xF9 / 255f),
                    0.8f to Color.Black.copy(alpha = 0xB2 / 255f),
                    1.0f to Color.Transparent,
                ),
                startX = gradientLeft,
                endX = gradientLeft + gradientSpan,
            ),
            topLeft = Offset.Zero,
            size = size,
        )

        // --- inner mask: hides the selected hour label on the ring (shown big in TimeRange instead) ---
        val maskWidth = LabelWidth.toPx()
        val maskHeight = LabelHeight.toPx()
        val maskCenter = Offset(center.x + labelRadius + labelLeftOffset, center.y)
        val maskTop = maskCenter.y - maskHeight / 2f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.25f to Color.Black,
                    0.75f to Color.Black,
                    1.0f to Color.Transparent,
                ),
                startY = maskTop,
                endY = maskTop + maskHeight,
            ),
            topLeft = Offset(maskCenter.x - maskWidth / 2f, maskTop),
            size = Size(maskWidth, maskHeight),
            cornerRadius = CornerRadius(maskHeight / 2f),
        )

        // Fade the visible ruler into the black background at both viewport ends.
        // This is drawn underneath TimeRange, so it never dims the right-hand picker.
        val fadeHeight = min(80.dp.toPx(), size.height / 2f)
        val rulerWidth = min(size.width, center.x + labelRadius + labelLeftOffset + maskWidth / 2f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = 0f,
                endY = fadeHeight,
            ),
            size = Size(rulerWidth, fadeHeight),
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = size.height - fadeHeight,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - fadeHeight),
            size = Size(rulerWidth, fadeHeight),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 393, heightDp = 420)
@Composable
private fun CircleTimePreview() {
    CircleTime(selectedDuration = { 5f })
}
