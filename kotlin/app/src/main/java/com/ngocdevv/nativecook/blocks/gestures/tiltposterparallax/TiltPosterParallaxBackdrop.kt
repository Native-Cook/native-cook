package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun BoxScope.TiltPosterParallaxBackdrop(
    engine: TiltPosterParallaxEngine,
    art: TiltPosterParallaxArt,
    stageWidth: Dp,
    stageHeight: Dp,
) {
    val density = LocalDensity.current.density
    Image(
        bitmap = art.backdrop,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .offset(x = (-Tokens.BLEED).dp, y = (-Tokens.BLEED).dp)
            .size(
                width = stageWidth + (Tokens.BLEED * 2.0).dp,
                height = stageHeight * Tokens.LAYOUT.backdropShare.toFloat() + (Tokens.BLEED * 2.0).dp,
            )
            .graphicsLayer {
                translationX = (engine.tiltX * Tokens.PARALLAX.backdrop * density).toFloat()
                translationY = (engine.tiltY * Tokens.PARALLAX.backdrop * density).toFloat()
                alpha = 0.85f
            },
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Tokens.COLOR.backdropFade[0],
                    0.35f to Tokens.COLOR.backdropFade[1],
                    Tokens.LAYOUT.backdropShare.toFloat() to Tokens.COLOR.backdropFade[2],
                ),
                endY = size.height,
            ),
        )
    }
}
