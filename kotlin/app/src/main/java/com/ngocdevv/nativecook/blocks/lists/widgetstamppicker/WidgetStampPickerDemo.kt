package com.ngocdevv.nativecook.blocks.lists.widgetstamppicker

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun WidgetStampPickerDemo() {
    WidgetStampPicker(autoPlay = true)
}

@Preview
@Composable
private fun WidgetStampPickerDemoPreview() {
    WidgetStampPickerDemo()
}
