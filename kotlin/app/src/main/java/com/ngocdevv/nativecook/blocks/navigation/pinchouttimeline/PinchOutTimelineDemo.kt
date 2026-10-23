package com.ngocdevv.nativecook.blocks.navigation.pinchouttimeline

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. Its page is
 * light, so it asks for dark status bar icons while it is on screen.
 */
@Composable
fun PinchOutTimelineDemo() {
    PinchOutTimeline(autoPlay = true)
}

@Preview
@Composable
private fun PinchOutTimelineDemoPreview() {
    PinchOutTimelineDemo()
}
