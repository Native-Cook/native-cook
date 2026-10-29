package com.ngocdevv.nativecook.blocks.pickers.glassmoodbloom

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Petalog's mood check-in on the whole screen, sliding through every mood by itself. */
@Composable
fun GlassMoodBloomDemo() {
    GlassMoodBloom(autoPlay = true)
}

@Preview
@Composable
private fun GlassMoodBloomDemoPreview() {
    GlassMoodBloomDemo()
}
