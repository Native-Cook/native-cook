package com.ngocdevv.nativecook.blocks.navigation.hoppingdottabbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. The
 * gallery's activity is edge to edge with light icons on both bars, which is what the blue screen
 * needs. It starts on the last tab, so the first hop is the long one.
 */
@Composable
fun HoppingDotTabBarDemo() {
    HoppingDotTabBar(initialTab = 3, autoPlay = true)
}

@Preview
@Composable
private fun HoppingDotTabBarDemoPreview() {
    HoppingDotTabBarDemo()
}
