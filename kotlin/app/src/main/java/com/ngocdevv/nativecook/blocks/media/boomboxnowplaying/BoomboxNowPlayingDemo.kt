package com.ngocdevv.nativecook.blocks.media.boomboxnowplaying

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun BoomboxNowPlayingDemo() {
    BoomboxNowPlaying(autoPlay = true)
}

@Preview
@Composable
private fun BoomboxNowPlayingDemoPreview() {
    BoomboxNowPlayingDemo()
}
