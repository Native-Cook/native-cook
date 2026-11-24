package com.ngocdevv.nativecook.blocks.media.comparerevealslider

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// A project page of an invented interior studio's app, Roomwork. One living room, rendered before
// and after from the same camera, sits under a divider with mass. Drag the photo to take over
// (the film returns 2 s after the last touch).
@Composable
fun CompareRevealSliderDemo() {
    CompareRevealSlider(autoPlay = true)
}

@Preview
@Composable
private fun CompareRevealSliderDemoPreview() {
    CompareRevealSliderDemo()
}
