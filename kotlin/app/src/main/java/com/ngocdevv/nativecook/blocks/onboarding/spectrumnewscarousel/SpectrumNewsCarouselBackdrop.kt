package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer

/** One glow of the warm field: the centre and radii as fractions of the screen, its colour and strength. */
private class SpectrumNewsCarouselBlob(
    val x: Float,
    val y: Float,
    val rx: Float,
    val ry: Float,
    val color: Color,
    val alpha: Float,
)

/**
 * `blobs` of `WarmBackdrop`: a light field, not a flat gradient: hot magenta in the top-right corner,
 * amber on the left, a blush of green underfoot, and each edge glowing faintly inward like a lit
 * screen bezel.
 */
private val SpectrumNewsCarouselBlobs = listOf(
    SpectrumNewsCarouselBlob(0.98f, 0.0f, 0.78f, 0.34f, Color(0xFFF06BD8), 0.78f),
    SpectrumNewsCarouselBlob(0.02f, 0.18f, 0.62f, 0.32f, Color(0xFFFFAA3D), 0.72f),
    SpectrumNewsCarouselBlob(0.38f, 0.06f, 0.62f, 0.2f, Color(0xFFFFE16A), 0.7f),
    SpectrumNewsCarouselBlob(0.64f, 0.14f, 0.5f, 0.2f, Color(0xFFFF92B6), 0.5f),
    SpectrumNewsCarouselBlob(-0.04f, 0.48f, 0.18f, 0.36f, Color(0xFFFFBE4A), 0.55f),
    SpectrumNewsCarouselBlob(1.04f, 0.46f, 0.17f, 0.42f, Color(0xFFF27FCD), 0.52f),
    SpectrumNewsCarouselBlob(0.22f, 1.04f, 0.62f, 0.13f, Color(0xFF94D67F), 0.62f),
    SpectrumNewsCarouselBlob(0.88f, 1.03f, 0.44f, 0.11f, Color(0xFFF4A2CB), 0.42f),
)

/**
 * `WarmBackdrop`: the screen-sized warm field, shrunk to the welcome window while the window grows
 * (translate to the window's centre, scale to its size), fading out as the carousel leaves page one
 * and in with the welcome glow. Each glow is a radial gradient on an ellipse, stops at 0, 55% and
 * 100% of the radius with alpha 1, 0.38 and 0 times the blob's strength.
 */
@Composable
internal fun SpectrumNewsCarouselBackdrop(drive: SpectrumNewsCarouselDrive, modifier: Modifier = Modifier) {
    val brushes = remember {
        SpectrumNewsCarouselBlobs.map { blob ->
            Brush.radialGradient(
                0f to blob.color.copy(alpha = blob.alpha),
                0.55f to blob.color.copy(alpha = blob.alpha * 0.38f),
                1f to blob.color.copy(alpha = 0f),
                center = Offset.Zero,
                radius = 1f,
            )
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val frame = drive.welcomeFrame()
                alpha = (SpectrumNewsCarouselMotion.clamp(1.0 - drive.position) * drive.welcomeGlow()).toFloat()
                translationX = ((frame.x + frame.width / 2.0 - 0.5) * size.width).toFloat()
                translationY = ((frame.y + frame.height / 2.0 - 0.5) * size.height).toFloat()
                scaleX = frame.width.toFloat()
                scaleY = frame.height.toFloat()
                // `overflow: hidden`.
                clip = true
            }
            .background(Color(0xFFFCFBF8))
            .drawBehind {
                SpectrumNewsCarouselBlobs.forEachIndexed { index, blob ->
                    withTransform({
                        translate(left = blob.x * size.width, top = blob.y * size.height)
                        scale(scaleX = blob.rx * size.width, scaleY = blob.ry * size.height, pivot = Offset.Zero)
                    }) {
                        drawCircle(brushes[index], radius = 1f, center = Offset.Zero)
                    }
                }
            },
    )
}
