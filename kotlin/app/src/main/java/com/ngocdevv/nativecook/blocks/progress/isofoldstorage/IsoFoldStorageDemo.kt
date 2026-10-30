package com.ngocdevv.nativecook.blocks.progress.isofoldstorage

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun IsoFoldStorageDemo() {
    IsoFoldStorage(autoPlay = true)
}

@Preview
@Composable
private fun IsoFoldStorageDemoPreview() {
    IsoFoldStorageDemo()
}
