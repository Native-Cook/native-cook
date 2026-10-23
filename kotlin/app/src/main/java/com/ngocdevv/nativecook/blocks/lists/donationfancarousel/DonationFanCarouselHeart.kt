package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Hearts

/**
 * `Heart` in `ReactionMarks.tsx`: one of three hearts that rise and fade in over the active
 * portrait. It is a `react-native-svg` drawing with `viewBox="0 0 32 30"` that fills its square box
 * (the default `xMidYMid meet`): the heart path filled with a bounding-box gradient and a short
 * rounded highlight stroked over its upper left. It is centred in its parent and moved from there.
 * The box is [Hearts.Bleed] larger than the heart on every side, so its shadow and the fade of the
 * layer are not cut at the heart's edge.
 */
@Composable
internal fun DonationFanCarouselHeart(index: Int, state: DonationFanCarouselState, scale: Float) {
    val placement = Hearts.Placements[index]
    val side = placement.size * scale
    Box(
        modifier = Modifier
            .requiredSize((side + Hearts.Bleed * 2f).dp)
            .graphicsLayer {
                val amount = DonationFanCarouselMotion.heartsAt(state.clock)
                // `Math.max(0, Math.min(1, amount * 1.35 - index * 0.08))`.
                val delayed = maxOf(0.0, minOf(1.0, amount * Hearts.StaggerGain - index * Hearts.StaggerStep))
                val grow = DonationFanCarouselMath.interpolate(
                    delayed,
                    doubleArrayOf(0.0, 1.0),
                    doubleArrayOf(Hearts.ScaleFrom, 1.0),
                ).toFloat()
                alpha = delayed.toFloat()
                translationX = placement.x * scale * density
                translationY = (placement.y + placement.rise * (1f - delayed.toFloat())) * scale * density
                rotationZ = placement.rotate
                scaleX = grow
                scaleY = grow
            }
            .donationFanCarouselHeartArtwork(side),
    )
}
