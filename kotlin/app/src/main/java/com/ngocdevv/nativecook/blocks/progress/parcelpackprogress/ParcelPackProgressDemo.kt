package com.ngocdevv.nativecook.blocks.progress.parcelpackprogress

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ParcelPackProgressDemo() {
    ParcelPackProgress(autoPlay = true)
}

@Preview
@Composable
private fun ParcelPackProgressDemoPreview() {
    ParcelPackProgressDemo()
}
