package com.ngocdevv.nativecook.blocks.text.revolvingtextring

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. Its
 * colours invert as it plays, so it switches the bar icons itself and puts them back on close.
 */
@Composable
fun RevolvingTextRingDemo() {
    RevolvingTextRing(autoPlay = true)
}

@Preview
@Composable
private fun RevolvingTextRingDemoPreview() {
    RevolvingTextRingDemo()
}
