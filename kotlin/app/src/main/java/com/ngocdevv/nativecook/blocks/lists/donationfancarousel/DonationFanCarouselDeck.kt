package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The `StyleSheet.absoluteFill` view of the React Native block that holds the ten portraits and,
 * above them all (`zIndex: 130`), the active portrait's keyline and reactions. Portraits are
 * stacked by `zIndex` (100 minus 20 per step of distance); equal values keep the array's order.
 * Only the stacking order is read here, so the deck recomposes when two portraits swap places and
 * not on every frame.
 */
@Composable
internal fun DonationFanCarouselDeck(
    state: DonationFanCarouselState,
    geometry: DonationFanCarouselGeometry,
) {
    val ranks by remember(state) {
        derivedStateOf(structuralEqualityPolicy()) { DonationFanCarouselPose.stackRanks(state.page) }
    }
    Box(modifier = Modifier.size(geometry.stageWidth.dp, geometry.stageHeight.dp)) {
        DonationFanCarouselCard.All.forEachIndexed { slot, card ->
            DonationFanCarouselCardView(
                card = card,
                state = state,
                geometry = geometry,
                stackRank = ranks[slot],
            )
        }
        DonationFanCarouselActiveBorder(state = state, geometry = geometry)
    }
}
