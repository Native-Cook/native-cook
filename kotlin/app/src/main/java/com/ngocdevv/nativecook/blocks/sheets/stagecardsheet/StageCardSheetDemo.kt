package com.ngocdevv.nativecook.blocks.sheets.stagecardsheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The block fills the screen and keeps the status bar, so it is shown without a stage. */
@Composable
fun StageCardSheetDemo() {
    StageCardSheet(autoPlay = true)
}

@Preview
@Composable
private fun StageCardSheetDemoPreview() {
    StageCardSheetDemo()
}
