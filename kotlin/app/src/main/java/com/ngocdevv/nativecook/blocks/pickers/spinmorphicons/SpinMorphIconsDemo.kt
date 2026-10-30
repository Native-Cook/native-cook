package com.ngocdevv.nativecook.blocks.pickers.spinmorphicons

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The block fills its viewport and handles its own safe area. */
@Composable
fun SpinMorphIconsDemo() {
    SpinMorphIcons(autoPlay = true)
}

@Preview
@Composable
private fun SpinMorphIconsDemoPreview() {
    SpinMorphIconsDemo()
}
