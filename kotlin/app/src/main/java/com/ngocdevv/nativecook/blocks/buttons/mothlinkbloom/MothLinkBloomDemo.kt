package com.ngocdevv.nativecook.blocks.buttons.mothlinkbloom

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MothLinkBloomDemo() {
    MothLinkBloom(modifier = Modifier, autoPlay = true)
}

@Preview
@Composable
private fun MothLinkBloomDemoPreview() {
    MothLinkBloomDemo()
}
