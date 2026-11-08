package com.ngocdevv.nativecook.blocks.gestures.hoopshotrefresh

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The Courtside feed on the whole screen, pulling and shooting by itself. */
@Composable
fun HoopShotRefreshDemo() {
    HoopShotRefresh(autoPlay = true)
}

@Preview
@Composable
private fun HoopShotRefreshDemoPreview() {
    HoopShotRefreshDemo()
}
