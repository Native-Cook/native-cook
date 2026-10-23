package com.ngocdevv.nativecook.blocks.gestures.holddragresize

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun HoldDragResizeDemo() {
    HoldDragResize(autoPlay = true)
}

@Preview
@Composable
private fun HoldDragResizeDemoPreview() {
    HoldDragResizeDemo()
}
