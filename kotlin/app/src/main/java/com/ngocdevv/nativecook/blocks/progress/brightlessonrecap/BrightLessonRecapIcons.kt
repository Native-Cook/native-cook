package com.ngocdevv.nativecook.blocks.progress.brightlessonrecap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

private fun svgPath(data: String): Path = PathParser().parsePathString(data).toPath()

/**
 * The glyph of a result pill: a filled bolt, a sealed check or a clock face, drawn from the SVG
 * of `StatIcon` (BrightLessonRecap.tsx:40-45) in its 24 x 24 view box.
 */
@Composable
internal fun BrightLessonRecapStatIcon(kind: BrightLessonRecapStatKind, color: Color, size: Dp) {
    val bolt = remember { svgPath("M13.4 2.2 4.8 13.4h6.1l-1.1 8.4 8.9-11.6h-6.2l.9-8Z") }
    val check = remember { svgPath("m7.8 12.2 2.9 2.9 5.6-6.2") }
    val hands = remember { svgPath("M12 6.8V12l3.4 2.1") }
    Canvas(Modifier.size(size)) {
        val factor = this.size.width / 24f
        scale(factor, factor, pivot = Offset.Zero) {
            when (kind) {
                BrightLessonRecapStatKind.Points -> {
                    drawPath(bolt, color)
                    drawPath(bolt, color, style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                BrightLessonRecapStatKind.Accuracy -> {
                    drawCircle(color, 9.6f, Offset(12f, 12f))
                    drawPath(check, Color.White, style = Stroke(2.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                BrightLessonRecapStatKind.Time -> {
                    drawCircle(color, 9.6f, Offset(12f, 12f))
                    drawPath(hands, Color.White, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/** The four-point sparkle of the hero, `Sparkle`'s SVG in a 20 x 20 view box (BrightLessonRecap.tsx:87-94). */
@Composable
internal fun BrightLessonRecapSparkleGlyph(color: Color, size: Dp, modifier: Modifier = Modifier) {
    val shape = remember {
        svgPath("M10 0C10.9 6.4 13.6 9.1 20 10 13.6 10.9 10.9 13.6 10 20 9.1 13.6 6.4 10.9 0 10 6.4 9.1 9.1 6.4 10 0Z")
    }
    Canvas(modifier.size(size)) {
        val factor = this.size.width / 20f
        scale(factor, factor, pivot = Offset.Zero) { drawPath(shape, color) }
    }
}

/** The share arrow out of a tray, in a 24 x 24 view box (BrightLessonRecap.tsx:151). */
@Composable
internal fun BrightLessonRecapShareIcon(color: Color, size: Dp) {
    val strokes = remember {
        listOf(
            svgPath("M12 14.5V3.5"),
            svgPath("M8 7.2 12 3.3l4 3.9"),
            svgPath("M8.5 10.5H7a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2h-1.5"),
        )
    }
    Canvas(Modifier.size(size)) {
        val factor = this.size.width / 24f
        scale(factor, factor, pivot = Offset.Zero) { drawShareStrokes(strokes, color) }
    }
}

private fun DrawScope.drawShareStrokes(strokes: List<Path>, color: Color) {
    val style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    strokes.forEach { drawPath(it, color, style = style) }
}
