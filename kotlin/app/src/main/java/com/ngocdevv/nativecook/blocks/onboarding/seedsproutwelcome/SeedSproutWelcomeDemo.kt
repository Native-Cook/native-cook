package com.ngocdevv.nativecook.blocks.onboarding.seedsproutwelcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen edge to edge and keeps its content clear of the system bars itself,
 * so it is shown without a stage.
 */
@Composable
fun SeedSproutWelcomeDemo() {
    SeedSproutWelcome(autoPlay = true)
}

@Preview
@Composable
private fun SeedSproutWelcomeDemoPreview() {
    SeedSproutWelcomeDemo()
}
