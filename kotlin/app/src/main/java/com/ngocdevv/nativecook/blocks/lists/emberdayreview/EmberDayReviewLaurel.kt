package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** The five leaves of a sprig: x, y of the leaf's base and its rotation in degrees (`leaves` in `Laurel`). */
private val Leaves = arrayOf(
    floatArrayOf(13f, 30f, -38f),
    floatArrayOf(10.5f, 23f, -22f),
    floatArrayOf(9.5f, 16f, -6f),
    floatArrayOf(10.5f, 9.5f, 12f),
    floatArrayOf(13.5f, 4f, 30f),
)

/**
 * A laurel sprig drawn as five leaves on a curved stem in a 22 x 36 dp box (the `Laurel` SVG); [flip] mirrors it for
 * the right side of the summary card.
 */
@Composable
internal fun EmberDayReviewLaurel(flip: Boolean, modifier: Modifier = Modifier) {
    val stem = remember {
        Path().apply {
            moveTo(16f, 34f)
            cubicTo(9f, 28f, 7f, 18f, 13f, 3f)
        }
    }
    val leaf = remember {
        Path().apply {
            moveTo(0f, 0f)
            // M{x} {y} c-5-1-7-4-7-7 4 0 7 2 7 7 Z, built at the origin and moved to each leaf's base.
            relativeCubicTo(-5f, -1f, -7f, -4f, -7f, -7f)
            relativeCubicTo(4f, 0f, 7f, 2f, 7f, 7f)
            close()
        }
    }
    Canvas(modifier.size(22.dp, 36.dp).graphicsLayer { scaleX = if (flip) -1f else 1f }) {
        scale(density, density, pivot = Offset.Zero) {
            drawPath(stem, EmberDayReviewPalette.LaurelStem, style = Stroke(width = 1.4f, cap = StrokeCap.Round))
            Leaves.forEach { (x, y, degrees) ->
                withTransform({
                    translate(x, y)
                    rotate(degrees, Offset.Zero)
                }) {
                    drawPath(leaf, EmberDayReviewPalette.LaurelLeaf)
                }
            }
        }
    }
}
