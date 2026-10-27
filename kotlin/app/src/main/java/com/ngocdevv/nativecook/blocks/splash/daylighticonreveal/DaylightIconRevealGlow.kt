package com.ngocdevv.nativecook.blocks.splash.daylighticonreveal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * `glow`: day on the left, night on the right, the icon's two lights spilling faintly onto the page.
 * Two elliptical radial gradients (the React Native `Svg`): a warm one (#FFD35A at 42%) centred at
 * 36% of the width and a cool one (#5B4BE0 at 34%) at 64%, both at the icon's resting height, each
 * 42% of the width wide and 36% tall in radius, fading to nothing at the rim. The whole layer
 * fades in from 1.4 s to 2.6 s up to 55% and out from 7.45 s to 8.1 s; Reduce Motion shows it at 55%.
 * The drawing is clipped to the hero, like the `Svg` it replaces.
 */
@Composable
internal fun DaylightIconRevealGlow(
    time: () -> Double,
    reduced: Boolean,
    width: Float,
    height: Float,
    iconCenterY: Float,
) {
    val timeline = DaylightIconRevealTimeline
    Canvas(
        Modifier
            .size(width.dp, height.dp)
            .graphicsLayer {
                val t = time()
                alpha = if (reduced) {
                    0.55f
                } else {
                    (0.55 * timeline.segment(t, 1.4, 2.6) * (1.0 - timeline.segment(t, 7.45, 8.1))).toFloat()
                }
            },
    ) {
        if (size.width <= 0f) return@Canvas
        clipRect {
            drawEllipseGlow(Offset(width * 0.36f * density, iconCenterY * density), width * 0.42f * density, width * 0.36f * density, Color(0xFFFFD35A), 0.42f)
            drawEllipseGlow(Offset(width * 0.64f * density, iconCenterY * density), width * 0.42f * density, width * 0.36f * density, Color(0xFF5B4BE0), 0.34f)
        }
    }
}

/**
 * An ellipse filled by a radial gradient that runs from [alpha] at its centre to nothing at its rim.
 * A radial gradient is round, so the ellipse is a circle of radius [rx] squashed vertically to [ry].
 */
private fun DrawScope.drawEllipseGlow(center: Offset, rx: Float, ry: Float, color: Color, alpha: Float) {
    withTransform({
        translate(center.x, center.y)
        scale(1f, ry / rx, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                center = Offset.Zero,
                radius = rx,
            ),
            radius = rx,
            center = Offset.Zero,
        )
    }
}
