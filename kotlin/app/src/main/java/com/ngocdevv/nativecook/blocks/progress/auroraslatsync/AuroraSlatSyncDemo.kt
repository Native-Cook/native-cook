package com.ngocdevv.nativecook.blocks.progress.auroraslatsync

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The Halo Vault sync hero with its eight-second autoplay loop. */
@Composable
fun AuroraSlatSyncDemo() {
    AuroraSlatSync(autoPlay = true)
}

@Preview
@Composable
private fun AuroraSlatSyncDemoPreview() {
    AuroraSlatSyncDemo()
}
