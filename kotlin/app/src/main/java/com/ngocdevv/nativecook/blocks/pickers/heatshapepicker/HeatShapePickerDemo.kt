package com.ngocdevv.nativecook.blocks.pickers.heatshapepicker

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun HeatShapePickerDemo() {
    HeatShapePicker(autoPlay = true)
}

@Preview
@Composable
private fun HeatShapePickerDemoPreview() {
    HeatShapePickerDemo()
}
