package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Coin
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Colors
import kotlin.math.min

/** The disc, its shadow, its inner ring and its seeds, centred in the box. */
internal fun Modifier.donationFanCarouselCoinArtwork(scale: Float): Modifier = drawWithCache {
    val d = density
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val radius = Coin.Size * scale / 2f * d
    val border = Coin.Border * d

    val shadow = DonationFanCarouselSoftShadow(Colors.CoinShadow, Coin.ShadowRadius / 2f * d)
    val silhouette = android.graphics.Path().apply {
        addCircle(centerX, centerY, radius, android.graphics.Path.Direction.CW)
    }
    val shadowOffsetY = Coin.ShadowOffsetY * d

    // `width: '72%'` of the coin's content box, which is inside its 1 dp border.
    val inner = Coin.InnerFraction * (Coin.Size * scale - Coin.Border * 2f) * d
    val innerBorder = Coin.InnerBorder * d
    // A radius larger than half the side is clamped, as React Native does.
    val innerRadius = min(Coin.InnerRadius * scale * d, inner / 2f)
    val innerLeft = centerX - inner / 2f
    val innerTop = centerY - inner / 2f

    // `alignItems: 'flex-end'`, `gap: 2`, `paddingBottom: 3`, inside the 1 dp border.
    val seedWidth = Coin.SeedWidth * scale * d
    val seedGap = Coin.InnerGap * d
    val seedsWidth = seedWidth * Coin.SeedHeights.size + seedGap * (Coin.SeedHeights.size - 1)
    val seedsLeft = centerX - seedsWidth / 2f
    val seedsBottom = innerTop + inner - innerBorder - Coin.InnerPaddingBottom * d
    val seedRadius = min(Coin.SeedRadius * d, seedWidth / 2f)

    onDrawBehind {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(0f, shadowOffsetY)
            shadow.draw(native, silhouette)
            native.restore()
        }
        drawCircle(color = Colors.Accent, radius = radius, center = Offset(centerX, centerY))
        // The 1 dp border is drawn inside the disc's edge.
        drawCircle(
            color = Colors.AccentDark,
            radius = radius - border / 2f,
            center = Offset(centerX, centerY),
            style = Stroke(width = border),
        )
        drawRoundRect(
            color = Colors.CoinInnerBorder,
            topLeft = Offset(innerLeft + innerBorder / 2f, innerTop + innerBorder / 2f),
            size = Size(inner - innerBorder, inner - innerBorder),
            cornerRadius = CornerRadius(maxOf(0f, innerRadius - innerBorder / 2f)),
            style = Stroke(width = innerBorder),
        )
        Coin.SeedHeights.forEachIndexed { index, height ->
            val seedHeight = height * scale * d
            drawRoundRect(
                color = Colors.CoinSeed,
                topLeft = Offset(seedsLeft + index * (seedWidth + seedGap), seedsBottom - seedHeight),
                size = Size(seedWidth, seedHeight),
                cornerRadius = CornerRadius(seedRadius),
            )
        }
    }
}
