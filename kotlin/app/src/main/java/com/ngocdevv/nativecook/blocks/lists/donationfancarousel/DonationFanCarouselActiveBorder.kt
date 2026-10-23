package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Colors
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Layout

/**
 * `styles.activeBorder` in the React Native block: a box over the active portrait with the lime
 * keyline (hidden while the fan travels) and the hearts and coin on top of it.
 */
@Composable
internal fun DonationFanCarouselActiveBorder(
    state: DonationFanCarouselState,
    geometry: DonationFanCarouselGeometry,
) {
    val shape = RoundedCornerShape(geometry.cardRadius.dp)
    Box(
        modifier = Modifier
            .offset(geometry.deckLeft.dp, geometry.deckTop.dp)
            .size(geometry.cardSize.dp)
            .zIndex(Layout.Deck.ActiveBorderZ),
        contentAlignment = Alignment.Center,
    ) {
        // `borderWidth: layout.deck.border * scale` is drawn inside the box.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = DonationFanCarouselMotion.borderAt(state.clock).toFloat() }
                .border((Layout.Deck.Border * geometry.scale).dp, Colors.Accent, shape),
        )
        DonationFanCarouselReactionMarks(state = state, scale = geometry.scale)
    }
}
