package com.ngocdevv.nativecook.blocks.media.fisheyememorywall

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The memory wall on the whole screen, panning and bulging by itself. */
@Composable
fun FisheyeMemoryWallDemo() {
    FisheyeMemoryWall(autoPlay = true)
}

@Preview
@Composable
private fun FisheyeMemoryWallDemoPreview() {
    FisheyeMemoryWallDemo()
}
