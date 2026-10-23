package com.ngocdevv.nativecook.blocks.navigation.railtobarmorph

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen, so it is shown without a stage. It hides the status bar while it is
 * open and sets the navigation bar's icons itself.
 */
@Composable
fun RailToBarMorphDemo() {
    RailToBarMorph(autoPlay = true)
}

@Preview
@Composable
private fun RailToBarMorphDemoPreview() {
    RailToBarMorphDemo()
}
