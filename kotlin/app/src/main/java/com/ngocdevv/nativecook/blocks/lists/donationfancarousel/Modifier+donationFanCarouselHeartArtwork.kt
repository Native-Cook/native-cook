package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Colors
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Hearts

/** The heart, its shadow and its highlight, drawn into a box [sideDp] wide plus the bleed. */
internal fun Modifier.donationFanCarouselHeartArtwork(sideDp: Float): Modifier = drawWithCache {
    val bleed = Hearts.Bleed * density
    val side = sideDp * density
    // `preserveAspectRatio="xMidYMid meet"` on a 32 x 30 view box in a square.
    val fit = minOf(side / Hearts.ViewBoxWidth, side / Hearts.ViewBoxHeight)
    val originX = bleed + (side - Hearts.ViewBoxWidth * fit) / 2f
    val originY = bleed + (side - Hearts.ViewBoxHeight * fit) / 2f

    val shadow = DonationFanCarouselSoftShadow(Colors.HeartShadow, Hearts.ShadowRadius / 2f * density)
    val silhouette = heartPath { x, y -> Offset(originX + x * fit, originY + y * fit) }.asAndroidPath()
    val shadowOffsetY = Hearts.ShadowOffsetY * density
    // `<LinearGradient x1="0" y1="0" x2="1" y2="1">` is in the path's bounding-box units, so the
    // fill is drawn as a unit square that is stretched over the 28 x 26 bounds.
    val unitHeart = heartPath { x, y -> Offset((x - BoundsX) / BoundsWidth, (y - BoundsY) / BoundsHeight) }
    val gradient = Brush.linearGradient(
        0f to Colors.HeartLight,
        0.42f to Colors.Heart,
        1f to Colors.HeartDark,
        start = Offset.Zero,
        end = Offset(1f, 1f),
    )
    val highlight = Path().apply {
        moveTo(originX + 7.3f * fit, originY + 6.8f * fit)
        cubicTo(
            originX + 9f * fit, originY + 4.9f * fit,
            originX + 11.5f * fit, originY + 5.1f * fit,
            originX + 13.1f * fit, originY + 6.7f * fit,
        )
    }

    onDrawBehind {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(0f, shadowOffsetY)
            shadow.draw(native, silhouette)
            native.restore()
        }
        withTransform({
            translate(originX + BoundsX * fit, originY + BoundsY * fit)
            scale(BoundsWidth * fit, BoundsHeight * fit, pivot = Offset.Zero)
        }) {
            drawPath(unitHeart, brush = gradient)
        }
        drawPath(
            highlight,
            color = Colors.HeartHighlight,
            style = Stroke(width = Hearts.HighlightWidth * fit, cap = StrokeCap.Round),
        )
    }
}

/** The heart path's bounding box in the 32 x 30 view box (`M16 28C12.8 23.8 2 17.5 ...`). */
private const val BoundsX = 2f
private const val BoundsY = 2f
private const val BoundsWidth = 28f
private const val BoundsHeight = 26f

/**
 * `M16 28C12.8 23.8 2 17.5 2 9.4C2 4.9 5.2 2 9.1 2C12 2 14.3 3.7 16 6.2C17.7 3.7 20 2 22.9 2C26.8 2
 * 30 4.9 30 9.4C30 17.5 19.2 23.8 16 28Z`, every point passed through [map].
 */
private fun heartPath(map: (Float, Float) -> Offset): Path {
    return Path().apply {
        fun point(x: Float, y: Float) = map(x, y)
        fun curve(control1: Offset, control2: Offset, end: Offset) {
            cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
        }
        val start = point(16f, 28f)
        moveTo(start.x, start.y)
        curve(point(12.8f, 23.8f), point(2f, 17.5f), point(2f, 9.4f))
        curve(point(2f, 4.9f), point(5.2f, 2f), point(9.1f, 2f))
        curve(point(12f, 2f), point(14.3f, 3.7f), point(16f, 6.2f))
        curve(point(17.7f, 3.7f), point(20f, 2f), point(22.9f, 2f))
        curve(point(26.8f, 2f), point(30f, 4.9f), point(30f, 9.4f))
        curve(point(30f, 17.5f), point(19.2f, 23.8f), point(16f, 28f))
        close()
    }
}
