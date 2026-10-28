package com.ngocdevv.nativecook.blocks.cards.pearljeweldrawer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun PearlJewelDrawerDemo() {
    PearlJewelDrawer(modifier = Modifier, autoPlay = true)
}

@Preview
@Composable
private fun PearlJewelDrawerDemoPreview() {
    PearlJewelDrawerDemo()
}
