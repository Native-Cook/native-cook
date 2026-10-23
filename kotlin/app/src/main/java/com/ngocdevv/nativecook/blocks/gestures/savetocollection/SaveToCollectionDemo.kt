package com.ngocdevv.nativecook.blocks.gestures.savetocollection

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SaveToCollectionDemo() {
    SaveToCollection(autoPlay = true)
}

@Preview
@Composable
private fun SaveToCollectionDemoPreview() {
    SaveToCollectionDemo()
}
