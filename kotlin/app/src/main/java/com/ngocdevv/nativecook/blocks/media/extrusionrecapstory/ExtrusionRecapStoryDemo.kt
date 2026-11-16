package com.ngocdevv.nativecook.blocks.media.extrusionrecapstory

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** LOOP = [ExtrusionRecapStoryMotion.LOOP] s; the close mark goes back, as the RN demo's router does. */
@Composable
fun ExtrusionRecapStoryDemo() {
    ExtrusionRecapStory(autoPlay = true)
}

@Preview
@Composable
private fun ExtrusionRecapStoryDemoPreview() {
    ExtrusionRecapStoryDemo()
}
