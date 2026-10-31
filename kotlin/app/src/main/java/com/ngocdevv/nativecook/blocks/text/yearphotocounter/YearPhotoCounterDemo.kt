package com.ngocdevv.nativecook.blocks.text.yearphotocounter

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun YearPhotoCounterDemo() {
    YearPhotoCounter(autoPlay = true)
}

@Preview
@Composable
private fun YearPhotoCounterDemoPreview() {
    YearPhotoCounterDemo()
}
