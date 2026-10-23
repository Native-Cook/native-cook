package com.ngocdevv.nativecook.blocks.charts.swipemonthdonut

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val DEGREES = (180 / PI).toFloat()

/**
 * The month's spending as a ring of round-capped segments with gaps, drawn clockwise from the top.
 * Each segment's start and sweep come from the shares, not from a stored path, so a segment that
 * leaves shrinks into its neighbour's end and one that arrives grows out of it; one shorter than
 * its round caps is drawn as a dot that shrinks to nothing instead.
 *
 * @param gap The visible gap between two segments, in dp along the ring.
 * @param pop How far a tapped segment moves out.
 * @param shares The share of each category, interpolated by the pager.
 * @param focus The tapped category, or -1.
 * @param out How far it is out (0 to 1).
 */
@Composable
internal fun DonutRing(
    size: Float,
    stroke: Float,
    gap: Float,
    pop: Float,
    colors: List<Color>,
    shares: () -> List<Float>,
    focus: () -> Int,
    out: () -> Float,
    modifier: Modifier = Modifier,
) {
    // The canvas leaves room round the ring for a segment that pops out.
    val inset = stroke / 2 + pop + 1
    val radius = size / 2 - inset
    // Half a gap plus a round cap, as an angle: what each end of a segment gives up.
    val trim = (stroke / 2 + gap / 2) / radius * DEGREES

    Canvas(modifier.size(size.dp)) {
        val list = shares()
        val tapped = focus()
        val away = out()
        scale(density, density, pivot = Offset.Zero) {
            var at = -90f
            for (i in list.indices) {
                if (i >= colors.size) break
                val raw = list[i] * 360
                val sweep = raw - trim * 2
                val mid = (at + raw / 2) / DEGREES
                val shift = if (i == tapped) pop * away else 0f
                // Shorter than its two caps: a dot, as wide as there is room for.
                val width = stroke * clamp01(raw / (trim * 2))
                val start = if (sweep > 0) at + trim else at + raw / 2
                at += raw
                if (width <= 0.2f) continue
                translate(cos(mid) * shift, sin(mid) * shift) {
                    drawArc(
                        color = colors[i],
                        startAngle = start,
                        sweepAngle = max(sweep, 0.01f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size - inset * 2, size - inset * 2),
                        style = Stroke(width, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}
