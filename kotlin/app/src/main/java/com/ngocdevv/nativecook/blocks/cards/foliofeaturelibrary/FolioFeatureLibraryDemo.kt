package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The Folio Feature Library on the whole screen, playing its 39 s guided loop by itself. */
@Composable
fun FolioFeatureLibraryDemo() {
    FolioFeatureLibrary(autoPlay = true)
}

@Preview
@Composable
private fun FolioFeatureLibraryDemoPreview() {
    FolioFeatureLibraryDemo()
}
