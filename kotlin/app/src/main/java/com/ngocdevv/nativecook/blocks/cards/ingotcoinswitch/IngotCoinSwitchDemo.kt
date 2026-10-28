package com.ngocdevv.nativecook.blocks.cards.ingotcoinswitch

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The Explore screen on the whole screen, switching its ingots into coins and back by itself. */
@Composable
fun IngotCoinSwitchDemo() {
    IngotCoinSwitch(autoPlay = true)
}

@Preview
@Composable
private fun IngotCoinSwitchDemoPreview() {
    IngotCoinSwitchDemo()
}
