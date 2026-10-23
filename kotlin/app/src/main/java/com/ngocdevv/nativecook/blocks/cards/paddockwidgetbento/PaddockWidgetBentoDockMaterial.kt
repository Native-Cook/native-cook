package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.dp

/** The dock's glass: only the feed behind it is blurred (RenderEffect, API 31+); its buttons never enter the layer. */
@Composable
internal fun PaddockWidgetBentoDockMaterial(
    feedLayer: GraphicsLayer,
    scale: Float,
    viewportHeight: Float,
    modifier: Modifier,
) {
    Box(modifier) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Canvas(
                Modifier.matchParentSize().graphicsLayer {
                    renderEffect = BlurEffect(20.dp.toPx(), 20.dp.toPx(), TileMode.Clamp)
                },
            ) {
                val left = (PaddockTheme.REFERENCE_WIDTH - PaddockTheme.dockWidth) / 2f * scale * density
                val top = (viewportHeight - (PaddockTheme.dockBottom + PaddockTheme.dockHeight) * scale) * density
                translate(-left, -top) { drawLayer(feedLayer) }
            }
            Box(Modifier.matchParentSize().background(PaddockTheme.dockFallback))
        } else {
            // A dark material fallback, supported on API 26.
            Box(Modifier.matchParentSize().background(Color(0xE6171518)))
        }
    }
}
