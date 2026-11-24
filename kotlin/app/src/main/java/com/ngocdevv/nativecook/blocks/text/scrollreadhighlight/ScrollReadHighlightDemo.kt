package com.ngocdevv.nativecook.blocks.text.scrollreadhighlight

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ngocdevv.nativecook.shared.BlockSystemBars

/** The block fills the screen edge to edge; the page is dark, so the system bars get light icons. */
@Composable
fun ScrollReadHighlightDemo() {
    BlockSystemBars(darkIcons = false)
    ScrollReadHighlight(autoPlay = true)
}

@Preview
@Composable
private fun ScrollReadHighlightDemoPreview() {
    ScrollReadHighlightDemo()
}
