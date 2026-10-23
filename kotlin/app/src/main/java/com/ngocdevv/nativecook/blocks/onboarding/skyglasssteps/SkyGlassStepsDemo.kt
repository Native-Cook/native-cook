package com.ngocdevv.nativecook.blocks.onboarding.skyglasssteps

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The block fills the screen and keeps the status bar, so it is shown without a stage. */
@Composable
fun SkyGlassStepsDemo() {
    SkyGlassSteps(autoPlay = true)
}

@Preview
@Composable
private fun SkyGlassStepsDemoPreview() {
    SkyGlassStepsDemo()
}
