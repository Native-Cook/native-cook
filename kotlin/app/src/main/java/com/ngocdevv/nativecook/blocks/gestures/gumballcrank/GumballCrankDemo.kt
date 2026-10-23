package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun GumballCrankDemo() {
    GumballCrank(autoPlay = true)
}

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun GumballCrankDemoPreview() {
    GumballCrankDemo()
}
