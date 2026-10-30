package com.ngocdevv.nativecook.blocks.text.swisspostershuffle

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SwissPosterShuffleDemo() {
    SwissPosterShuffle(autoPlay = true)
}

@Preview
@Composable
private fun SwissPosterShuffleDemoPreview() {
    SwissPosterShuffleDemo()
}
