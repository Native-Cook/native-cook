package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun DragMultiSelectDemo() {
    DragMultiSelect(autoPlay = true)
}

@Preview
@Composable
private fun DragMultiSelectDemoPreview() {
    DragMultiSelectDemo()
}
