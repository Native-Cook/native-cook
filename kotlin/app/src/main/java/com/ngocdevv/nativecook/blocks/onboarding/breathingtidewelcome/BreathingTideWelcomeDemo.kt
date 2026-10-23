package com.ngocdevv.nativecook.blocks.onboarding.breathingtidewelcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The block fills the screen and sets the status bar's icons itself, so it is shown without a stage. */
@Composable
fun BreathingTideWelcomeDemo() {
    BreathingTideWelcome(autoPlay = true)
}

@Preview
@Composable
private fun BreathingTideWelcomeDemoPreview() {
    BreathingTideWelcomeDemo()
}
