package com.ngocdevv.nativecook.blocks.splash.sketchcoffeesplash

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The Inkpot Coffee Co. splash on the whole screen, playing by itself. */
@Composable
fun SketchCoffeeSplashDemo() {
    SketchCoffeeSplash(autoPlay = true)
}

@Preview
@Composable
private fun SketchCoffeeSplashDemoPreview() {
    SketchCoffeeSplashDemo()
}
