package com.ngocdevv.nativecook.blocks.media.shakedeveloppolaroid

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The block fills the screen and keeps the status bar, so it is shown without a stage. */
@Composable
fun ShakeDevelopPolaroidDemo() {
    ShakeDevelopPolaroid(autoPlay = true)
}

@Preview
@Composable
private fun ShakeDevelopPolaroidDemoPreview() {
    ShakeDevelopPolaroidDemo()
}
