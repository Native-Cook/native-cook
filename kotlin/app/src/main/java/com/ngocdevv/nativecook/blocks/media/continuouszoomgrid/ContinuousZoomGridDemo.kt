package com.ngocdevv.nativecook.blocks.media.continuouszoomgrid

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. The screen
 * is light, so the block asks for dark status bar icons while it is shown.
 */
@Composable
fun ContinuousZoomGridDemo() {
    ContinuousZoomGrid(autoPlay = true)
}

@Preview
@Composable
private fun ContinuousZoomGridDemoPreview() {
    ContinuousZoomGridDemo()
}
