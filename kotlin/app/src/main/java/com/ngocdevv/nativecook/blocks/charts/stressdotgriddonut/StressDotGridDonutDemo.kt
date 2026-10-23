package com.ngocdevv.nativecook.blocks.charts.stressdotgriddonut

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. The
 * gallery's activity is edge to edge with light icons on both bars, which is what the black screen
 * needs, so the block changes nothing on the window.
 */
@Composable
fun StressDotGridDonutDemo() {
    StressDotGridDonut(autoPlay = true)
}

@Preview
@Composable
private fun StressDotGridDonutDemoPreview() {
    StressDotGridDonutDemo()
}
