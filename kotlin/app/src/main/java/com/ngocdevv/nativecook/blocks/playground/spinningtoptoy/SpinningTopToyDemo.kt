package com.ngocdevv.nativecook.blocks.playground.spinningtoptoy

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SpinningTopToyDemo() {
    SpinningTopToy(autoPlay = true)
}

@Preview
@Composable
private fun SpinningTopToyDemoPreview() {
    SpinningTopToyDemo()
}
