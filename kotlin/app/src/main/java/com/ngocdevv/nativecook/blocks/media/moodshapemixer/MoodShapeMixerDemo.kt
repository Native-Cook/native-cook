package com.ngocdevv.nativecook.blocks.media.moodshapemixer

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. The
 * screen is light, so the block turns the system bars' icons dark while it is open.
 */
@Composable
fun MoodShapeMixerDemo(renderTime: Double? = null) {
    MoodShapeMixer(autoPlay = true, renderTime = renderTime)
}

@Preview
@Composable
private fun MoodShapeMixerDemoPreview() {
    MoodShapeMixerDemo()
}
