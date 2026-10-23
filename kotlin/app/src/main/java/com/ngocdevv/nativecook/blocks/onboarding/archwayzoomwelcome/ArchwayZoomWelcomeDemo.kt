package com.ngocdevv.nativecook.blocks.onboarding.archwayzoomwelcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. It turns
 * the icons of the system bars dark for its pale screen, and puts them back when it leaves.
 */
@Composable
fun ArchwayZoomWelcomeDemo() {
    ArchwayZoomWelcome(autoPlay = true)
}

@Preview
@Composable
private fun ArchwayZoomWelcomeDemoPreview() {
    ArchwayZoomWelcomeDemo()
}
