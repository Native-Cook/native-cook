package com.ngocdevv.nativecook.blocks.splash.cosmicdriftsplash

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The Orrery splash on the whole screen, playing by itself. */
@Composable
fun CosmicDriftSplashDemo() {
    CosmicDriftSplash(autoPlay = true)
}

@Preview
@Composable
private fun CosmicDriftSplashDemoPreview() {
    CosmicDriftSplashDemo()
}
