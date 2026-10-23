package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Colors

/**
 * `styles.stage` in the React Native block: the white 748 : 462 stage, centred on the window, with
 * the header, the fan and the two metadata blocks, in that order (later siblings draw over earlier
 * ones).
 */
@Composable
internal fun DonationFanCarouselStage(
    state: DonationFanCarouselState,
    geometry: DonationFanCarouselGeometry,
) {
    Box(
        modifier = Modifier
            .offset(geometry.stageLeft.dp, geometry.stageTop.dp)
            .size(geometry.stageWidth.dp, geometry.stageHeight.dp)
            .background(Colors.Background),
    ) {
        DonationFanCarouselHeader(geometry = geometry)
        DonationFanCarouselDeck(state = state, geometry = geometry)
        DonationFanCarouselMetadata(
            name = "Dorian Petters",
            amount = "\$100 donation • ",
            time = "10m ago",
            geometry = geometry,
            // `interpolate(page, [0, 0.55, 1.1], [1, 0.35, 0], Extrapolation.CLAMP)`.
            opacity = {
                DonationFanCarouselMath.interpolate(
                    state.page,
                    doubleArrayOf(0.0, 0.55, 1.1),
                    doubleArrayOf(1.0, 0.35, 0.0),
                    clamp = true,
                )
            },
        )
        DonationFanCarouselMetadata(
            name = "Emmy Stockley",
            amount = "\$200 donation • ",
            time = "11m ago",
            geometry = geometry,
            // `interpolate(page, [1.9, 2.45, 3], [0, 0.35, 1], Extrapolation.CLAMP)`.
            opacity = {
                DonationFanCarouselMath.interpolate(
                    state.page,
                    doubleArrayOf(1.9, 2.45, 3.0),
                    doubleArrayOf(0.0, 0.35, 1.0),
                    clamp = true,
                )
            },
        )
    }
}
