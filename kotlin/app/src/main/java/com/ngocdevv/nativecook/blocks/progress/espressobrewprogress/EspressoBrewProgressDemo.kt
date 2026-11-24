package com.ngocdevv.nativecook.blocks.progress.espressobrewprogress

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun EspressoBrewProgressDemo() {
    EspressoBrewProgress(autoPlay = true)
}

@Preview
@Composable
private fun EspressoBrewProgressDemoPreview() {
    EspressoBrewProgressDemo()
}
