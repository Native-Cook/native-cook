package com.ngocdevv.nativecook.blocks.media.glowfileuploader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.cos
import kotlin.math.sin

// The glyphs, drawn as paths in dp (callers draw inside `scale(density)`), each in its own box.

/** The paperclip and the circular arrows are drawn in a 15 dp box, then scaled to their size. */
internal const val GLYPH = 15f

/**
 * A paperclip standing upright in the [GLYPH] box (outer loop at the bottom, the wire's two ends at
 * the top); it is drawn turned 45° so it leans like "/", as in the design.
 */
private val Paperclip = Path().apply {
    moveTo(8.8f, 4.0f)
    lineTo(8.8f, 8.8f)
    arcTo(Rect(Offset(7.5f, 8.8f), 1.3f), 0f, 180f, false)
    lineTo(6.2f, 4.9f)
    arcTo(Rect(Offset(8.8f, 4.9f), 2.6f), 180f, 180f, false)
    lineTo(11.4f, 8.8f)
    arcTo(Rect(Offset(7.5f, 8.8f), 3.9f), 0f, 180f, false)
    lineTo(3.6f, 5.0f)
}

/** The turn that makes the upright clip lean like the design's. */
private const val CLIP_TURN = 45f

/**
 * Draws the paperclip [size] dp across centred on [center], turned by [turn] degrees more than its
 * lean; [stroke] is in dp whatever the size.
 */
internal fun DrawScope.paperclip(center: Offset, size: Float, color: Color, stroke: Float, turn: Float = 0f) {
    val k = size / GLYPH
    translate(center.x - GLYPH / 2f, center.y - GLYPH / 2f) {
        rotate(CLIP_TURN + turn, Offset(GLYPH / 2f, GLYPH / 2f)) {
            scale(k, k, Offset(GLYPH / 2f, GLYPH / 2f)) {
                drawPath(Paperclip, color, style = Stroke(stroke / k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// Two arcs chasing each other clockwise, each ending in a filled arrowhead.
private const val ARROWS_R = 5.0f
private const val ARROW_ARC = 125f
private const val ARROW_HEAD = 2.1f
private val ArrowStarts = floatArrayOf(205f, 25f)

private val ArrowArcs = Path().apply {
    for (start in ArrowStarts) arcTo(Rect(Offset(GLYPH / 2f, GLYPH / 2f), ARROWS_R), start, ARROW_ARC, true)
}

private val ArrowHeads = Path().apply {
    for (start in ArrowStarts) {
        val a = Math.toRadians((start + ARROW_ARC).toDouble())
        val px = GLYPH / 2f + ARROWS_R * cos(a).toFloat()
        val py = GLYPH / 2f + ARROWS_R * sin(a).toFloat()
        // Along the arc (clockwise) and out from the centre.
        val tx = -sin(a).toFloat()
        val ty = cos(a).toFloat()
        val nx = cos(a).toFloat()
        val ny = sin(a).toFloat()
        moveTo(px + tx * ARROW_HEAD * 1.1f, py + ty * ARROW_HEAD * 1.1f)
        lineTo(px + nx * ARROW_HEAD - tx * 0.3f, py + ny * ARROW_HEAD - ty * 0.3f)
        lineTo(px - nx * ARROW_HEAD - tx * 0.3f, py - ny * ARROW_HEAD - ty * 0.3f)
        close()
    }
}

/** The circular arrows of "Retry", [size] dp across from the origin, turned by [turn] degrees. */
internal fun DrawScope.circleArrows(size: Float, color: Color, stroke: Float, turn: Float) {
    val k = size / GLYPH
    scale(k, k, Offset.Zero) {
        rotate(turn, Offset(GLYPH / 2f, GLYPH / 2f)) {
            drawPath(ArrowArcs, color, style = Stroke(stroke / k, cap = StrokeCap.Round))
            drawPath(ArrowHeads, color)
        }
    }
}

/** A check in a 20 dp box (the done disc's). */
internal val DiscCheck = Path().apply {
    moveTo(6.0f, 10.3f)
    lineTo(8.8f, 13.0f)
    lineTo(14.1f, 7.4f)
}

/** A check in a 16 dp box (Continue's "Uploaded"). */
internal val LabelCheck = Path().apply {
    moveTo(3.0f, 8.3f)
    lineTo(6.4f, 11.6f)
    lineTo(13.0f, 4.8f)
}

/** Draws the first [trim] of a path, keeping its measure and a scratch path between frames. */
internal class TrimmedPath(private val source: Path) {
    private val measure = PathMeasure().apply { setPath(source, false) }
    private val part = Path()

    fun draw(scope: DrawScope, trim: Float, color: Color, stroke: Float, alpha: Float = 1f) {
        if (trim <= 0f) return
        val style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (trim >= 1f) {
            scope.drawPath(source, color, alpha, style)
            return
        }
        part.reset()
        measure.getSegment(0f, measure.length * trim, part, true)
        scope.drawPath(part, color, alpha, style)
    }
}

/** The white "!" of the failed pill, in a 14 dp disc (callers scale it to theirs). */
internal fun DrawScope.bang(color: Color) {
    drawLine(color, Offset(7f, 3.7f), Offset(7f, 7.9f), 1.8f, StrokeCap.Round)
    drawCircle(color, 0.95f, Offset(7f, 10.4f))
}
