package com.ngocdevv.nativecook.blocks.sheets.prismunsealsheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The delivery sheet fills the screen and keeps the system status bar. */
@Composable
fun PrismUnsealSheetDemo() {
    PrismUnsealSheet(autoPlay = true)
}

@Preview
@Composable
private fun PrismUnsealSheetDemoPreview() {
    PrismUnsealSheet(autoPlay = true, previewTime = 4.0)
}
