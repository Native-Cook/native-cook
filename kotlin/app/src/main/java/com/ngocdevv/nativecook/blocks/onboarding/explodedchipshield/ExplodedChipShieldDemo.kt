package com.ngocdevv.nativecook.blocks.onboarding.explodedchipshield

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Ferrin's security page on the whole screen, exploding its chip by itself. */
@Composable
fun ExplodedChipShieldDemo() {
    ExplodedChipShield(autoPlay = true)
}

@Preview
@Composable
private fun ExplodedChipShieldDemoPreview() {
    ExplodedChipShieldDemo()
}
