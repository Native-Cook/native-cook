package com.ngocdevv.nativecook.blocks.onboarding.denimpocketwelcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** A full-screen journal cover with the same eight-second loop as the Swift reference. */
@Composable
fun DenimPocketWelcomeDemo() {
    DenimPocketWelcome(autoPlay = true)
}

@Preview
@Composable
private fun DenimPocketWelcomeDemoPreview() {
    DenimPocketWelcomeDemo()
}
