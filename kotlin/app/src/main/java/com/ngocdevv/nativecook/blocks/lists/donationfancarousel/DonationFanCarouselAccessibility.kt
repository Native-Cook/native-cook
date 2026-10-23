package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

internal object DonationFanCarouselAccessibility {
    fun value(index: Int): String {
        return when (index.coerceIn(0, 3)) {
            0 -> "Dorian Petters, \$100 donation, 10 minutes ago, portrait 1 of 4"
            3 -> "Emmy Stockley, \$200 donation, 11 minutes ago, portrait 4 of 4"
            else -> "Portrait ${index + 1} of 4"
        }
    }
}
