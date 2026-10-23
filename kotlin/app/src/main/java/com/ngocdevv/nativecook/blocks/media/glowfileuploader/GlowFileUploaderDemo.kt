package com.ngocdevv.nativecook.blocks.media.glowfileuploader

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage; it switches
 * the system bars to light icons itself while it is open.
 */
@Composable
fun GlowFileUploaderDemo() {
    GlowFileUploader(autoPlay = true)
}

@Preview
@Composable
private fun GlowFileUploaderDemoPreview() {
    GlowFileUploaderDemo()
}
