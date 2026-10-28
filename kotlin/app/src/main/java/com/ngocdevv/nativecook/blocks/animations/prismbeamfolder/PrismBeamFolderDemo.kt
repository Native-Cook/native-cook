package com.ngocdevv.nativecook.blocks.animations.prismbeamfolder

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The Larder save screen on the whole screen, pouring picks into its folder by itself. */
@Composable
fun PrismBeamFolderDemo() {
    PrismBeamFolder(autoPlay = true)
}

@Preview
@Composable
private fun PrismBeamFolderDemoPreview() {
    PrismBeamFolderDemo()
}
