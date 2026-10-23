package com.ngocdevv.nativecook.blocks.splash.glowsearchsplash

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun GlowSearchSplashDemo() {
    GlowSearchSplash(autoPlay = true)
}

@Preview
@Composable
private fun GlowSearchSplashDemoPreview() {
    GlowSearchSplash(previewTime = 5.0)
}
