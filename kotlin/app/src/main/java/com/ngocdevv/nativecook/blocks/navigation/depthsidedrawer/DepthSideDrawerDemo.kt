package com.ngocdevv.nativecook.blocks.navigation.depthsidedrawer

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. It sets
 * the bars' icons itself, dark over the page and light over the menu.
 */
@Composable
fun DepthSideDrawerDemo() {
    DepthSideDrawer(autoPlay = true)
}

@Preview
@Composable
private fun DepthSideDrawerDemoPreview() {
    DepthSideDrawerDemo()
}
