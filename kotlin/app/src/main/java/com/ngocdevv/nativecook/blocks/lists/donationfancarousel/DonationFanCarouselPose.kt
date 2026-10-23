package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where one portrait sits in the fan: the animated style of `CardSlot` in the React Native block.
 * One instance per card is reused every frame. The transform is a translation, then a rotation, then
 * a scale about the card's centre, applied innermost first.
 */
internal class DonationFanCarouselPose {
    var x = 0.0
        private set
    var y = 0.0
        private set
    var scale = 1.0
        private set
    var rotation = 0.0
        private set
    var opacity = 1.0
        private set
    var zIndex = 0.0
        private set

    /** [distance] is the card's index minus the page. */
    fun update(distance: Double, layoutScale: Double) {
        val fan = DonationFanCarouselTheme.Fan
        val absolute = min(3.0, abs(distance))
        val direction = if (distance < 0) -1.0 else 1.0
        x = DonationFanCarouselMath.fan(absolute, fan.X, clamp = true) * direction * layoutScale
        y = DonationFanCarouselMath.fan(absolute, fan.Y, clamp = true) * layoutScale
        scale = DonationFanCarouselMath.fan(absolute, fan.Scale, clamp = true)
        rotation = DonationFanCarouselMath.fan(absolute, fan.Rotate, clamp = true) * direction
        opacity = if (absolute >= 3) max(0.0, 1 - (absolute - 3) * 4) else 1.0
        zIndex = DonationFanCarouselMath.jsRound(100 - absolute * 20)
    }

    companion object {
        /**
         * The drawing order of the ten cards at [page]: each card's place from the back (0) to the
         * front, by `zIndex`, with equal values keeping the array's order. Compose only moves a
         * card in the stack when this list changes, so the order is computed apart from the poses.
         */
        fun stackRanks(page: Double): List<Int> {
            val cards = DonationFanCarouselCard.All
            val depth = cards.map { card ->
                DonationFanCarouselMath.jsRound(100 - min(3.0, abs(card.index - page)) * 20)
            }
            val order = cards.indices.sortedWith(compareBy<Int>({ depth[it] }, { it }))
            val ranks = MutableList(cards.size) { 0 }
            order.forEachIndexed { rank, slot -> ranks[slot] = rank }
            return ranks
        }
    }
}
