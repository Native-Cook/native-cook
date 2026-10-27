package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp

/**
 * `QuietSignalDiagram`: one of the five monoline diagrams, a [size] square drawn on a 300-unit
 * grid. It is not part of the touch tree.
 *
 * [time] and [live] are read while drawing, so a frame redraws this canvas and recomposes nothing.
 * A diagram that is not [live] (and not reduced) draws nothing: it is invisible anyway, and
 * rebuilding its paths would be wasted. Under [reduced] it is drawn once, still, and never reads
 * the clock.
 */
@Composable
internal fun QuietSignalOnboardingDiagram(
    stage: Int,
    size: Dp,
    time: () -> Double,
    live: () -> Boolean,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val painter = remember { QuietSignalOnboardingDiagramPainter() }
    Canvas(modifier.size(size)) {
        if (!reduced && !live()) return@Canvas
        val t = if (reduced) 0.0 else time()
        val scale = this.size.width / GRID
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val saved = native.save()
            native.scale(scale, scale)
            painter.draw(native, stage, t, reduced)
            native.restoreToCount(saved)
        }
    }
}

/** The side of the square the diagrams are drawn on, before they are scaled to the screen. */
private const val GRID = 300f
