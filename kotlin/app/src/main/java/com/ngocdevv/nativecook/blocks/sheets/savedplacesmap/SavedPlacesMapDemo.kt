package com.ngocdevv.nativecook.blocks.sheets.savedplacesmap

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SavedPlacesMapDemo() {
    SavedPlacesMap(autoPlay = true)
}

@Preview(widthDp = 402, heightDp = 874)
@Composable
private fun SavedPlacesMapDemoPreview() {
    SavedPlacesMapDemo()
}
