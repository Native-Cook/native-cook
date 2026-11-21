package com.ngocdevv.nativecook.blocks.media.islandprintcamera

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun IslandPrintCameraDemo() {
    IslandPrintCamera(autoPlay = true)
}

@Preview
@Composable
private fun IslandPrintCameraDemoPreview() {
    IslandPrintCameraDemo()
}
