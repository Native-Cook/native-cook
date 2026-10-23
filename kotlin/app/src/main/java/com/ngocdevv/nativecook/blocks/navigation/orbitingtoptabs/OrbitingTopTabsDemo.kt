package com.ngocdevv.nativecook.blocks.navigation.orbitingtoptabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. It gives
 * the status bar dark icons itself, for its white page.
 */
@Composable
fun OrbitingTopTabsDemo() {
    OrbitingTopTabs(autoPlay = true)
}

@Preview
@Composable
private fun OrbitingTopTabsDemoPreview() {
    OrbitingTopTabsDemo()
}
