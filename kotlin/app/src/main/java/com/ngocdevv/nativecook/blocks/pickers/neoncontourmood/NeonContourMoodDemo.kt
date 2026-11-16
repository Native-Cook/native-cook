package com.ngocdevv.nativecook.blocks.pickers.neoncontourmood

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** LOOP = [NeonContourMoodMotion.LOOP] s; sound is on for the finger only, autoplay is silent. */
@Composable
fun NeonContourMoodDemo() {
    NeonContourMood(autoPlay = true, sound = true)
}

@Preview
@Composable
private fun NeonContourMoodDemoPreview() {
    NeonContourMoodDemo()
}

