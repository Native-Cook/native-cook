package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * `ReactionMarks` in the React Native block: the three hearts and the coin, centred on the active
 * portrait (a zero-size layer at its centre, as in `styles.layer`) and drawn above its keyline.
 * Place it in a box that centres its children.
 */
@Composable
internal fun DonationFanCarouselReactionMarks(state: DonationFanCarouselState, scale: Float) {
    Box(
        modifier = Modifier.requiredSize(0.dp),
        contentAlignment = Alignment.Center,
    ) {
        for (index in 0 until 3) {
            DonationFanCarouselHeart(index = index, state = state, scale = scale)
        }
        DonationFanCarouselCoin(state = state, scale = scale)
    }
}
