package com.ngocdevv.nativecook.blocks.media.posterringband

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun PosterRingBandDemo() {
    PosterRingBand(autoPlay = true)
}

@Preview
@Composable
private fun PosterRingBandDemoPreview() {
    PosterRingBandDemo()
}
