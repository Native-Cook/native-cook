package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.charts.pulseheartcard.PulseHeartCardMotion as M
import com.ngocdevv.nativecook.blocks.charts.pulseheartcard.PulseHeartCardTheme.Colors
import com.ngocdevv.nativecook.blocks.charts.pulseheartcard.PulseHeartCardTheme.Layout
import kotlin.math.max

/**
 * The range chart: 20 soft capsules, a dotted baseline and a cursor that follows the scrub.
 * [selected] drives the text; the cursor itself is drawn from the engine's frame on every [tick].
 */
@Composable
internal fun PulseHeartCardChart(
    scale: Float,
    engine: PulseHeartCardEngine,
    tick: State<Long>,
    selected: Int,
    onTouch: (Float) -> Unit,
    onEnd: () -> Unit,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plotWidth = (Layout.CHART_WIDTH * scale).dp
    val description = if (selected >= 0) "${M.samples[selected]} BPM at ${M.sampleTime(selected)}" else "43 to 120 BPM. Latest reading 62 BPM"
    Box(
        modifier
            .size(plotWidth, (Layout.CHART_HEIGHT * scale).dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Heart rate history"
                stateDescription = description
                customActions = listOf(
                    CustomAccessibilityAction("Next reading") { onAdjust(1); true },
                    CustomAccessibilityAction("Previous reading") { onAdjust(-1); true },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onTouch(down.position.x / density)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) onTouch(change.position.x / density)
                    } while (change.pressed)
                    onEnd()
                }
            },
    ) {
        // Static: the bars (the pin's are soft-edged, a 0.8 pt blur matches them), the dotted baseline, the end ticks.
        Canvas(
            Modifier
                .offset(y = (Layout.CHART_PLOT_TOP * scale).dp)
                .size(plotWidth, (Layout.CHART_PLOT_HEIGHT * scale).dp),
        ) {
            val u = scale * density
            // Skia's blur mask takes a sigma; Android's BlurMaskFilter a radius (sigma = 0.57735 r + 0.5).
            val sigma = Layout.CHART_BAR_BLUR * u
            val paint = Paint().apply {
                color = Colors.chartPink.copy(alpha = 0.78f)
                asFrameworkPaint().maskFilter = BlurMaskFilter(max((sigma - 0.5f) / 0.57735f, 0.1f), BlurMaskFilter.Blur.NORMAL)
            }
            drawIntoCanvas { canvas ->
                for (bar in M.barBounds) {
                    for ((y0, y1) in bar.segments) {
                        val left = (bar.x - Layout.CHART_LEFT) * u
                        val top = (y0 - Layout.CHART_ORIGIN) * u
                        val radius = bar.width / 2 * u
                        canvas.nativeCanvas.drawRoundRect(left, top, left + bar.width * u, top + (y1 - y0) * u, radius, radius, paint.asFrameworkPaint())
                    }
                }
            }
            for (index in 0 until 33) {
                drawCircle(Colors.faint.copy(alpha = 0.55f), radius = 0.7f * u, center = Offset((2 + index * 10.35f + 0.7f) * u, 60.7f * u))
            }
            for (x in listOf(2f, 334f)) {
                drawLine(Colors.faint.copy(alpha = 0.7f), Offset(x * u, 57 * u), Offset(x * u, size.height), strokeWidth = u)
            }
        }

        // The cursor: the active bar, a hairline and the ring, faded by the engine (drawn per tick).
        Box(
            Modifier
                .offset(y = (Layout.CHART_PLOT_TOP * scale).dp)
                .size(plotWidth, (Layout.CHART_PLOT_HEIGHT * scale).dp)
                .drawBehind { drawCursor(engine, tick, scale) },
        )

        PulseHeartCardText(
            text = if (selected >= 0) "${M.samples[selected]} BPM · ${M.sampleTime(selected)}" else "43–120 BPM",
            size = Layout.CHART_TITLE_SIZE * scale,
            line = Layout.CHART_TITLE_LINE * scale,
            weight = FontWeight.SemiBold,
            color = Colors.ink,
            tabular = true,
            modifier = Modifier
                .padding(start = (Layout.CHART_TITLE_INSET * scale).dp)
                .height((Layout.CHART_TITLE_LINE * scale).dp),
        )

        Box(
            Modifier
                .offset(y = (Layout.CHART_LABEL_TOP * scale).dp)
                .size(plotWidth, (Layout.CHART_LABEL_LINE * scale).dp),
        ) {
            PulseHeartCardText("12AM", Layout.CHART_LABEL_SIZE * scale, Layout.CHART_LABEL_LINE * scale, FontWeight.Normal, Colors.faint, Modifier.align(Alignment.CenterStart))
            PulseHeartCardText("12AM", Layout.CHART_LABEL_SIZE * scale, Layout.CHART_LABEL_LINE * scale, FontWeight.Normal, Colors.faint, Modifier.align(Alignment.CenterEnd))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCursor(engine: PulseHeartCardEngine, tick: State<Long>, scale: Float) {
    val tickValue = tick.value
    check(tickValue >= 0)
    val frame = engine.frame
    val u = scale * density
    val opacity = frame.cursorOpacity.toFloat()
    val latest = M.barBounds.last()
    val bar = M.barBounds[if (frame.selected >= 0) frame.selected else M.barBounds.size - 1]
    val centreX = (bar.x - Layout.CHART_LEFT + bar.width / 2) * u
    val centreY = (bar.y0 - Layout.CHART_ORIGIN - 3) * u
    val latestX = (latest.x - Layout.CHART_LEFT + latest.width / 2) * u
    val latestY = (latest.y0 - Layout.CHART_ORIGIN - 3) * u
    if (frame.selected >= 0) {
        for ((y0, y1) in bar.segments) {
            drawRoundRect(
                Colors.heart.copy(alpha = opacity),
                topLeft = Offset((bar.x - Layout.CHART_LEFT) * u, (y0 - Layout.CHART_ORIGIN) * u),
                size = Size(bar.width * u, (y1 - y0) * u),
                cornerRadius = CornerRadius(bar.width / 2 * u),
            )
        }
    }
    drawRect(Colors.heart.copy(alpha = opacity * 0.25f), topLeft = Offset(centreX, 2 * u), size = Size(u, 58 * u))
    drawRing(centreX, centreY, u, opacity)
    drawRing(latestX, latestY, u, 1f - opacity)
}

/** A white dot with a 2.4 heart-red rim; 9 across, the rim inside it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRing(x: Float, y: Float, u: Float, alpha: Float) {
    drawCircle(Color.White.copy(alpha = alpha), radius = 4.5f * u, center = Offset(x, y))
    drawCircle(Colors.heart.copy(alpha = alpha), radius = 4.5f * u - 1.2f * u, center = Offset(x, y), style = Stroke(width = 2.4f * u))
}
