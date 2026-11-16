package com.ngocdevv.nativecook.blocks.cards.coinpilecollect

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun CoinPileCollectDemo() {
    CoinPileCollect(autoPlay = true)
}

@Preview
@Composable
private fun CoinPileCollectDemoPreview() {
    CoinPileCollectDemo()
}
