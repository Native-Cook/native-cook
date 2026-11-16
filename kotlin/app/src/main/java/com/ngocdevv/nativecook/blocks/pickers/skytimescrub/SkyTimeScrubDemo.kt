package com.ngocdevv.nativecook.blocks.pickers.skytimescrub

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The loop opens on golden hour: 18:06, twelve minutes before the 18:18 sunset, the sun 3.4 degrees above the horizon. */
@Composable
fun SkyTimeScrubDemo() {
    SkyTimeScrub(autoPlay = true, now = SkyTimeScrubMotion.DEMO_NOW)
}

@Preview
@Composable
private fun SkyTimeScrubDemoPreview() {
    SkyTimeScrubDemo()
}

