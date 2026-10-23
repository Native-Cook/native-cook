package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Colors

/**
 * `CardSlot` in the React Native block: a square portrait with rounded corners
 * at the fan's centre, moved, tilted and shrunk by its distance from the active page. The pose is
 * read in the layer block, so a frame redraws the layer without recomposing it; [stackRank] is the
 * card's place in the stack and only changes when two cards swap.
 */
@Composable
internal fun DonationFanCarouselCardView(
    card: DonationFanCarouselCard,
    state: DonationFanCarouselState,
    geometry: DonationFanCarouselGeometry,
    stackRank: Int,
) {
    val pose = remember { DonationFanCarouselPose() }
    Box(
        modifier = Modifier
            .offset(geometry.deckLeft.dp, geometry.deckTop.dp)
            .size(geometry.cardSize.dp)
            .zIndex(stackRank.toFloat())
            .graphicsLayer {
                pose.update(card.index - state.page, geometry.scale.toDouble())
                // The transform reads translate, rotate, scale outermost first; a layer applies the
                // scale first and the translation last, about the card's centre.
                translationX = pose.x.toFloat() * density
                translationY = pose.y.toFloat() * density
                rotationZ = pose.rotation.toFloat()
                scaleX = pose.scale.toFloat()
                scaleY = pose.scale.toFloat()
                alpha = pose.opacity.toFloat()
            }
            // RN clips the card's own shadow with overflow: hidden.
            .clip(RoundedCornerShape(geometry.cardRadius.dp))
            .background(Colors.CardFill),
    ) {
        Image(
            painter = painterResource(card.portrait),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
