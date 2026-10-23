package com.ngocdevv.nativecook.blocks.media.tapeshelfplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. The page
 * is light, so the block switches the bar icons to dark while it is open.
 */
@Composable
fun TapeShelfPlayerDemo() {
    TapeShelfPlayer(autoPlay = true)
}

@Preview
@Composable
private fun TapeShelfPlayerDemoPreview() {
    TapeShelfPlayerDemo()
}
