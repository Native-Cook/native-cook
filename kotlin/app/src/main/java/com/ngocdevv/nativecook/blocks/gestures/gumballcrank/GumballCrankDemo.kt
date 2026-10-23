package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun GumballCrankDemo() {
    GumballCrank(autoPlay = true)
}

@Preview
@Composable
private fun GumballCrankDemoPreview() {
    GumballCrankDemo()
}
