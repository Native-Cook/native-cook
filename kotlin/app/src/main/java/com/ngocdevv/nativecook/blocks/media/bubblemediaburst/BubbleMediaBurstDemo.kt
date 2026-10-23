package com.ngocdevv.nativecook.blocks.media.bubblemediaburst

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage; it switches
 * the system bars to dark icons itself while it is open.
 */
@Composable
fun BubbleMediaBurstDemo() {
    BubbleMediaBurst(autoPlay = true)
}

@Preview
@Composable
private fun BubbleMediaBurstDemoPreview() {
    BubbleMediaBurstDemo()
}
