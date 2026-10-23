package com.ngocdevv.nativecook.blocks.navigation.spotlighttabbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. The screen
 * is light grey, so the block asks for dark status bar icons while it shows.
 */
@Composable
fun SpotlightTabBarDemo() {
    SpotlightTabBar(autoPlay = true)
}

@Preview
@Composable
private fun SpotlightTabBarDemoPreview() {
    SpotlightTabBarDemo()
}
