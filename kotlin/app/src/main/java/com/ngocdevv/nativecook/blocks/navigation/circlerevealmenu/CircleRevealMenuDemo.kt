package com.ngocdevv.nativecook.blocks.navigation.circlerevealmenu

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. Its page is
 * white, so it asks the window for dark status bar icons while the menu is closed and gives the
 * window back as it found it.
 */
@Composable
fun CircleRevealMenuDemo() {
    CircleRevealMenu(autoPlay = true)
}

@Preview
@Composable
private fun CircleRevealMenuDemoPreview() {
    CircleRevealMenuDemo()
}
