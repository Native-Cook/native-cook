package com.ngocdevv.nativecook.blocks.media.ribbonresonanceplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun RibbonResonancePlayerDemo() {
    RibbonResonancePlayer(modifier = Modifier, autoPlay = true)
}

@Preview
@Composable
private fun RibbonResonancePlayerDemoPreview() {
    RibbonResonancePlayerDemo()
}
