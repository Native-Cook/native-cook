package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import kotlin.math.max
import kotlin.math.min

/**
 * The window-dependent numbers at the top of `NocturneProgramCarousel` (all in dp): the window's
 * size from `useWindowDimensions`, the safe-area insets from `useSafeAreaInsets`, and what the
 * React Native file derives from them.
 */
internal class NocturneProgramCarouselMetrics(
    val width: Float,
    val height: Float,
    val fontScale: Float,
    val insetTop: Float,
    val insetBottom: Float,
) {
    /** Large text switches to a compact layout: the header scrolls with the cards. */
    val largeText: Boolean = fontScale > 1.35f

    /** The scroll view and the card's measurement are rebuilt when this changes (`layoutKey`). */
    val layoutKey: String = "$width:$fontScale"

    val cardWidth: Float = min(width * 0.76f, 370f)
    val stride: Float = cardWidth * (0.735f / 0.7556f)
    val artHeight: Float = if (largeText) 210f else max(206f, min(304f, height - insetTop - insetBottom - 380f))
    val minimumCardHeight: Float = artHeight + 150f

    /** The footer and the tab bar keep the same bottom padding. */
    val footerBottom: Float = max(insetBottom - 6f, 8f)
}
