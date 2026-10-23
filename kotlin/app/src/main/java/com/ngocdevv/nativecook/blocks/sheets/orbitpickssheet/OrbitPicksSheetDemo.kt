package com.ngocdevv.nativecook.blocks.sheets.orbitpickssheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The block fills the screen and draws its own status bar area, so it is shown without a stage. */
@Composable
fun OrbitPicksSheetDemo() {
    OrbitPicksSheet(autoPlay = true)
}

@Preview
@Composable
private fun OrbitPicksSheetDemoPreview() {
    OrbitPicksSheetDemo()
}
