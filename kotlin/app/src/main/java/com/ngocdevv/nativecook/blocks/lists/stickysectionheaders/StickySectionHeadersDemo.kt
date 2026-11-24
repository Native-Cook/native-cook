package com.ngocdevv.nativecook.blocks.lists.stickysectionheaders

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Ledger's activity history: day headers stick, get pushed, and shrink into a pill with a rolling running total. */
@Composable
fun StickySectionHeadersDemo() {
    StickySectionHeaders(autoPlay = true)
}

@Preview
@Composable
private fun StickySectionHeadersDemoPreview() {
    StickySectionHeadersDemo()
}
