package com.ngocdevv.nativecook.blocks.lists.newspaperpilereader

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The morning pile on the whole screen, picking a paper and putting it back by itself. */
@Composable
fun NewspaperPileReaderDemo() {
    NewspaperPileReader(autoPlay = true)
}

@Preview
@Composable
private fun NewspaperPileReaderDemoPreview() {
    NewspaperPileReaderDemo()
}
