package com.ngocdevv.nativecook.blocks.sheets.cartflystepper

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. The black
 * area behind the sheet suits the gallery's light status bar icons, so the block changes nothing
 * on the window.
 */
@Composable
fun CartFlyStepperDemo() {
    CartFlyStepper(autoPlay = true)
}

@Preview
@Composable
private fun CartFlyStepperDemoPreview() {
    CartFlyStepperDemo()
}
