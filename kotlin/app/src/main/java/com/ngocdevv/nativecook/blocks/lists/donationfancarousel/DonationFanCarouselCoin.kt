package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Coin
import kotlin.math.abs
import kotlin.math.min

/**
 * The coin of `ReactionMarks.tsx`: a lime disc with a pale inner ring and three seeds. It follows
 * the fan slot of the donor it belongs to (the first donor before 2500 ms of the loop, the fourth
 * after) and scales in and out with `coinAt`. It is centred in its parent; the box is [Coin.Bleed]
 * larger than the disc on every side so the shadow and the fade of the layer are not cut.
 */
@Composable
internal fun DonationFanCarouselCoin(state: DonationFanCarouselState, scale: Float) {
    val fan = DonationFanCarouselTheme.Fan
    Box(
        modifier = Modifier
            .requiredSize((Coin.Size * scale + Coin.Bleed * 2f).dp)
            .graphicsLayer {
                val amount = DonationFanCarouselMotion.coinAt(state.clock)
                val t = DonationFanCarouselMotion.wrapped(state.clock)
                val selected = if (t < Coin.SecondHalfStartMs) 0.0 else 3.0
                val distance = selected - state.page
                val absolute = min(3.0, abs(distance))
                val direction = if (distance < 0) -1.0 else 1.0
                val appear = DonationFanCarouselMath.interpolate(
                    amount,
                    doubleArrayOf(0.0, 1.0),
                    doubleArrayOf(Coin.ScaleFrom, 1.0),
                )
                val grow = (DonationFanCarouselMath.fan(absolute, fan.Scale, clamp = false) * appear).toFloat()
                alpha = amount.toFloat()
                translationX = (DonationFanCarouselMath.fan(absolute, fan.X, clamp = false) * direction).toFloat() * scale * density
                translationY = DonationFanCarouselMath.fan(absolute, fan.Y, clamp = false).toFloat() * scale * density
                scaleX = grow
                scaleY = grow
            }
            .donationFanCarouselCoinArtwork(scale),
    )
}
