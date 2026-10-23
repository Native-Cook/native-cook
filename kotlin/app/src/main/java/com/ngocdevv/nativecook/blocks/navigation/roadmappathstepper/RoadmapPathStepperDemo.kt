package com.ngocdevv.nativecook.blocks.navigation.roadmappathstepper

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. The screen
 * is white, so the block asks for dark status bar icons while it shows.
 */
@Composable
fun RoadmapPathStepperDemo() {
    RoadmapPathStepper(autoPlay = true)
}

@Preview
@Composable
private fun RoadmapPathStepperDemoPreview() {
    RoadmapPathStepperDemo()
}
