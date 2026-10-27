package com.ngocdevv.nativecook.blocks.splash.daylighticonreveal

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp

/**
 * The icon layer: a canvas as large as the screen (anchored at the hero's top-left corner, so the
 * enlarged icon of the first second has room) that draws the projected artwork, inside a layer that
 * carries its opacity (the pose track times the recede) and the recede's blur.
 *
 * The blur is applied to the finished drawing instead of to the artwork before the projection, as
 * React Native does. At the time it is used (7.45 s on) the icon is at rest and the projection scales
 * by about 0.91, so the radius is multiplied by that factor to look the same.
 *
 * Everything reads [time] inside the layer and draw blocks, so a frame redraws only this canvas.
 */
@Composable
internal fun DaylightIconRevealIcon(
    bitmap: Bitmap,
    time: () -> Double,
    reduced: Boolean,
    left: Float,
    top: Float,
    artWidth: Float,
    artHeight: Float,
    areaWidth: Dp,
    areaHeight: Dp,
) {
    val painter = remember(bitmap) { DaylightIconRevealIconPainter(bitmap) }
    val restScale = remember(painter, artWidth, artHeight) { painter.restScale(artWidth, artHeight) }
    Canvas(
        Modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .size(areaWidth, areaHeight)
            .graphicsLayer {
                val t = time()
                alpha = painter.layerAlpha(t, reduced)
                val blurDp = painter.recede(t, reduced) * 8.0 * restScale
                renderEffect = daylightIconRevealBlur((blurDp * density).toFloat())
            },
    ) {
        val t = time()
        if (painter.layerAlpha(t, reduced) <= 0.001f) return@Canvas
        drawIntoCanvas { canvas ->
            painter.draw(canvas.nativeCanvas, density, t, reduced, left, top, artWidth, artHeight)
        }
    }
}
