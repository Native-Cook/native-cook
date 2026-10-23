package com.ngocdevv.nativecook.blocks.onboarding.logowindowreveal

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and draws under both bars, so it is shown without a stage. Its
 * screen is white at both ends of the show; an app that mounts it sets dark bar icons for it.
 */
@Composable
fun LogoWindowRevealDemo() {
    LogoWindowReveal(autoPlay = true)
}

@Preview
@Composable
private fun LogoWindowRevealDemoPreview() {
    LogoWindowRevealDemo()
}
