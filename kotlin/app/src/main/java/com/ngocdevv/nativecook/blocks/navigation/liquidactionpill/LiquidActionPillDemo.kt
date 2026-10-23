package com.ngocdevv.nativecook.blocks.navigation.liquidactionpill

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block is a whole screen with its own background, so it is shown without a stage. The
 * gallery's activity is edge to edge with light icons on both bars, which suits the mauve page, so
 * the block changes nothing on the window.
 */
@Composable
fun LiquidActionPillDemo() {
    LiquidActionPill(autoPlay = true)
}

@Preview
@Composable
private fun LiquidActionPillDemoPreview() {
    LiquidActionPillDemo()
}
