package com.ngocdevv.nativecook.blocks.media.silverscreencarousel

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The carousel on the whole screen, drifting by itself (`autoPlay`, a loop of [SILVER_SCREEN_CAROUSEL_LOOP] s)
 * under the status bar, as in React Native. A drag, a tap or a scroll takes over and two seconds later the
 * loop starts again from the first card.
 */
@Composable
fun SilverScreenCarouselDemo() {
    SilverScreenCarousel(autoPlay = true)
}

@Preview
@Composable
private fun SilverScreenCarouselDemoPreview() {
    SilverScreenCarouselDemo()
}
