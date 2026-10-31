package com.ngocdevv.nativecook.blocks.playground.discringstack

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun DiscRingStackDemo() {
    DiscRingStack(autoPlay = true)
}

@Preview
@Composable
private fun DiscRingStackDemoPreview() {
    DiscRingStackDemo()
}
