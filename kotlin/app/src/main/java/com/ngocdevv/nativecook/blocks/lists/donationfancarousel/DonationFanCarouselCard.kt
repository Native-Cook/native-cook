package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.annotation.DrawableRes
import com.ngocdevv.nativecook.R

/**
 * One portrait slot of the fan: `cards` in the React Native block. [index] is the slot's position
 * along the fan (the page that makes it the active portrait); the three donors repeat at both ends.
 */
internal class DonationFanCarouselCard(
    val id: String,
    val index: Int,
    @DrawableRes val portrait: Int,
) {
    companion object {
        val All = listOf(
            DonationFanCarouselCard("mateo-left", -3, R.drawable.donationfancarousel_mateo),
            DonationFanCarouselCard("andre-left", -2, R.drawable.donationfancarousel_andre),
            DonationFanCarouselCard("ruth-left", -1, R.drawable.donationfancarousel_ruth),
            DonationFanCarouselCard("noah", 0, R.drawable.donationfancarousel_noah),
            DonationFanCarouselCard("mei", 1, R.drawable.donationfancarousel_mei),
            DonationFanCarouselCard("theo", 2, R.drawable.donationfancarousel_theo),
            DonationFanCarouselCard("clara", 3, R.drawable.donationfancarousel_clara),
            DonationFanCarouselCard("andre-right", 4, R.drawable.donationfancarousel_andre),
            DonationFanCarouselCard("mateo-right", 5, R.drawable.donationfancarousel_mateo),
            DonationFanCarouselCard("ruth-right", 6, R.drawable.donationfancarousel_ruth),
        )
    }
}
