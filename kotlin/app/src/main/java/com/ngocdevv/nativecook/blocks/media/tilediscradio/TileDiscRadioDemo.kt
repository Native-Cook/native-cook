package com.ngocdevv.nativecook.blocks.media.tilediscradio

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The now-playing screen on the whole screen, changing station by itself. */
@Composable
fun TileDiscRadioDemo() {
    TileDiscRadio(autoPlay = true)
}

@Preview
@Composable
private fun TileDiscRadioDemoPreview() {
    TileDiscRadioDemo()
}
