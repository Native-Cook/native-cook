package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R

@Composable
internal fun BoxScope.TiltPosterParallaxBackdrop(
    engine: TiltPosterParallaxEngine,
    stageWidth: Dp,
    stageHeight: Dp,
) {
    val density = LocalDensity.current.density
    Image(
        painter = painterResource(R.drawable.tiltposterparallax_backdrop),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .offset(x = (-Tokens.BLEED).dp, y = (-Tokens.BLEED).dp)
            .size(
                width = stageWidth + (Tokens.BLEED * 2.0).dp,
                height = stageWidth * Tokens.LAYOUT.backdropAspect.toFloat() + Tokens.BLEED.dp,
            )
            .graphicsLayer {
                translationX = (engine.tiltX * Tokens.PARALLAX.backdrop * density).toFloat()
                translationY = (engine.tiltY * Tokens.PARALLAX.backdrop * density).toFloat()
            },
    )
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val lightRadius = Tokens.LAYOUT.stageLight.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Tokens.COLOR.stageLight, Tokens.COLOR.stageLightClear),
                center = Offset.Zero,
                radius = lightRadius,
            ),
            radius = lightRadius,
            center = Offset.Zero,
        )
        drawRect(
            brush = Brush.horizontalGradient(
                colors = Tokens.COLOR.backdropSide,
                startX = size.width * 0.2f,
                endX = size.width,
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Tokens.COLOR.backdropFade[0],
                    0.4f to Tokens.COLOR.backdropFade[1],
                    0.62f to Tokens.COLOR.backdropFade[2],
                ),
                startY = 0f,
                endY = stageHeight.toPx(),
            ),
        )
    }
}
