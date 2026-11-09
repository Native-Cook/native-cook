package com.ngocdevv.nativecook.blocks.progress.triplineloader

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The complete Farewind trip loader with its eight-second autoplay loop. */
@Composable
fun TripLineLoaderDemo() {
    TripLineLoader(autoPlay = true)
}

@Preview
@Composable
private fun TripLineLoaderDemoPreview() {
    TripLineLoaderDemo()
}
