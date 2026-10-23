package com.ngocdevv.nativecook.blocks.charts.stepsbarstospikes

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The block fills the screen and keeps the status bar, so it is shown without a stage. */
@Composable
fun StepsBarsToSpikesDemo() {
    StepsBarsToSpikes(autoPlay = true)
}

@Preview
@Composable
private fun Preview() {
    StepsBarsToSpikesDemo()
}
