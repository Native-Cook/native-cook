package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun CassetteDeckDemo() {
    CassetteDeck(autoPlay = true)
}

@Preview
@Composable
private fun CassetteDeckDemoPreview() {
    CassetteDeckDemo()
}
