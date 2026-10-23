package com.ngocdevv.nativecook.blocks.onboarding.shapeshufflewelcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and draws under both bars, so it is shown without a stage. Its
 * screen is white; an app that mounts it sets dark bar icons for it.
 */
@Composable
fun ShapeShuffleWelcomeDemo() {
    ShapeShuffleWelcome(autoPlay = true)
}

@Preview
@Composable
private fun ShapeShuffleWelcomeDemoPreview() {
    ShapeShuffleWelcomeDemo()
}
