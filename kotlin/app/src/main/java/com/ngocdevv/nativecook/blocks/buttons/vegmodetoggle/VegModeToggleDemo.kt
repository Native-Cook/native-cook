package com.ngocdevv.nativecook.blocks.buttons.vegmodetoggle

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The menu's diet toggle on the whole screen, flipping between its two dishes by itself. */
@Composable
fun VegModeToggleDemo() {
    VegModeToggle(autoPlay = true)
}

@Preview
@Composable
private fun VegModeToggleDemoPreview() {
    VegModeToggleDemo()
}
