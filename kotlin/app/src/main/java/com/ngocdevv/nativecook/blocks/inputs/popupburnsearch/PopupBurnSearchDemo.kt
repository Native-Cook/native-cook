package com.ngocdevv.nativecook.blocks.inputs.popupburnsearch

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** SupperSeek 99's search on the whole screen, typing, popping and burning its answers by itself. */
@Composable
fun PopupBurnSearchDemo() {
    PopupBurnSearch(autoPlay = true)
}

@Preview
@Composable
private fun PopupBurnSearchDemoPreview() {
    PopupBurnSearchDemo()
}
