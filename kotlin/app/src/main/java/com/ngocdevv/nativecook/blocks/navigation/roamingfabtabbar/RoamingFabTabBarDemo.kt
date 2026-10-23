package com.ngocdevv.nativecook.blocks.navigation.roamingfabtabbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block is a whole screen with its own background, so it is shown without a stage. Its page is
 * white, so while it is on screen it asks for dark icons on the system bars.
 */
@Composable
fun RoamingFabTabBarDemo() {
    RoamingFabTabBar(autoPlay = true)
}

@Preview
@Composable
private fun RoamingFabTabBarDemoPreview() {
    RoamingFabTabBarDemo()
}
