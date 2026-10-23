package com.ngocdevv.nativecook.blocks.lists.foryouinsights

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import kotlin.math.exp

private const val FOR_YOU_INSIGHTS_GLOW_LAYERS = 12
private val forYouInsightsGlowWeightTotal = (1..FOR_YOU_INSIGHTS_GLOW_LAYERS).sumOf { layer ->
    val fraction = layer.toDouble() / FOR_YOU_INSIGHTS_GLOW_LAYERS
    exp(-2.0 * fraction * fraction)
}.toFloat()

@Composable
fun ForYouInsightsTip(
    scale: Float,
    frame: State<ForYouInsightsFrame>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val type = ForYouInsightsTheme.Type.tip
    val widthPx = with(density) { (ForYouInsightsTheme.Layout.COLUMN_WIDTH * scale).toFloat().toDp().roundToPx() }
    val textLayout = remember(textMeasurer, scale, density.fontScale, widthPx) {
        textMeasurer.measure(
            text = ForYouInsightsContent.tip,
            style = TextStyle(
                color = ForYouInsightsTheme.Colors.ink,
                fontSize = (type.size * scale).sp,
                lineHeight = ((type.line ?: type.size) * scale).sp,
                fontWeight = type.weight,
                letterSpacing = (type.tracking * scale).sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            maxLines = ForYouInsightsTheme.Layout.TIP_LINES,
            constraints = Constraints(maxWidth = widthPx),
        )
    }
    val widths = remember(textLayout, scale, density.density) {
        List(textLayout.lineCount) { line ->
            (textLayout.getLineRight(line) - textLayout.getLineLeft(line)).toDouble() /
                (density.density * scale)
        }
    }

    Canvas(
        modifier.semantics {
            contentDescription = ForYouInsightsContent.tip
        },
    ) {
        val tip = frame.value.tip
        drawText(
            textLayoutResult = textLayout,
            color = ForYouInsightsTheme.Colors.ink,
            alpha = tip.ghost.toFloat().coerceIn(0f, 1f),
        )

        val heads = ForYouInsightsMotion.streamHeads(tip.stream, widths)
        val pixelsPerPoint = density.density * scale
        repeat(textLayout.lineCount) { line ->
            val left = textLayout.getLineLeft(line)
            val top = textLayout.getLineTop(line)
            val right = textLayout.getLineRight(line)
            val bottom = textLayout.getLineBottom(line)
            val head = heads.getOrElse(line) { 0.0 }.toFloat() * pixelsPerPoint
            val gradientStart = left + head -
                ForYouInsightsMotion.Stream.HEAD.toFloat() * pixelsPerPoint
            val inkEnd = gradientStart
                .coerceIn(left, right)
            if (inkEnd > left) {
                clipRect(left, top, inkEnd, bottom) {
                    drawText(textLayout, color = ForYouInsightsTheme.Colors.ink)
                }
            }
            val gradientEnd = (left + head).coerceIn(left, right)
            if (gradientEnd > inkEnd) {
                clipRect(inkEnd, top, gradientEnd, bottom) {
                    drawText(
                        textLayoutResult = textLayout,
                        brush = Brush.horizontalGradient(
                            0f to ForYouInsightsTheme.Colors.ink,
                            0.55f to ForYouInsightsTheme.Colors.pink,
                            1f to ForYouInsightsTheme.Colors.violet,
                            startX = gradientStart,
                            endX = gradientStart +
                                ForYouInsightsMotion.Stream.HEAD.toFloat() * pixelsPerPoint,
                        ),
                    )
                }
            }
        }

        if (tip.shimmer >= 0.0) {
            val shimmerWidth = ForYouInsightsMotion.Stream.SHIMMER_WIDTH.toFloat() * pixelsPerPoint
            val start = -ForYouInsightsMotion.Stream.SHIMMER_WIDTH.toFloat() * 1.4f * pixelsPerPoint
            val end = size.width + ForYouInsightsMotion.Stream.SHIMMER_WIDTH.toFloat() * 0.4f * pixelsPerPoint
            val shimmerX = ForYouInsightsMotion.lerp(start.toDouble(), end.toDouble(), tip.shimmer).toFloat()
            rotate(
                degrees = ForYouInsightsMotion.Stream.SHIMMER_SKEW.toFloat(),
                pivot = Offset(shimmerX + shimmerWidth / 2f, size.height / 2f),
            ) {
                clipRect(shimmerX, 0f, shimmerX + shimmerWidth, size.height) {
                    drawText(
                        textLayoutResult = textLayout,
                        brush = Brush.horizontalGradient(
                            colors = ForYouInsightsTheme.Colors.shimmer,
                            startX = shimmerX,
                            endX = shimmerX + shimmerWidth,
                        ),
                    )
                }
            }
        }

        if (tip.caret > 0.0 && widths.isNotEmpty()) {
            var line = widths.lastIndex
            for (index in widths.indices) {
                if (heads[index] < widths[index]) {
                    line = index
                    break
                }
            }
            val caretX = textLayout.getLineLeft(line) +
                minOf(heads[line], widths[line]).toFloat() * pixelsPerPoint +
                ForYouInsightsMotion.Stream.CARET_INSET.toFloat() * 0.5f * pixelsPerPoint
            val caretY = textLayout.getLineTop(line) + 5f * pixelsPerPoint
            val caretWidth = ForYouInsightsMotion.Stream.CARET_WIDTH.toFloat() * pixelsPerPoint
            val caretHeight = ((type.line ?: type.size) - 9.0).toFloat() * pixelsPerPoint
            val alpha = tip.caret.toFloat().coerceIn(0f, 1f)
            val glowRadius = 7f * pixelsPerPoint
            for (layer in FOR_YOU_INSIGHTS_GLOW_LAYERS downTo 1) {
                val fraction = layer.toFloat() / FOR_YOU_INSIGHTS_GLOW_LAYERS
                val expansion = glowRadius * fraction
                val weight = exp(-2.0 * fraction * fraction).toFloat() /
                    forYouInsightsGlowWeightTotal
                drawRoundRect(
                    color = ForYouInsightsTheme.Colors.caretGlow,
                    topLeft = Offset(caretX - expansion, caretY - expansion),
                    size = Size(
                        caretWidth + expansion * 2f,
                        caretHeight + expansion * 2f,
                    ),
                    cornerRadius = CornerRadius(caretWidth + expansion),
                    alpha = alpha * 0.9f * weight,
                )
            }
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        ForYouInsightsTheme.Colors.pink,
                        ForYouInsightsTheme.Colors.violet,
                    ),
                    startY = caretY,
                    endY = caretY + caretHeight,
                ),
                topLeft = Offset(caretX, caretY),
                size = Size(caretWidth, caretHeight),
                cornerRadius = CornerRadius(caretWidth),
                alpha = alpha,
            )
        }
    }
}
