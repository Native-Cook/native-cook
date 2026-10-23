package com.ngocdevv.nativecook.blocks.pickers.arcactivitypicker

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ArcActivityPickerDemo() {
    ComponentStage(background = Color(0xFFF3F3F3), height = 700.dp) { ArcActivityPicker(autoPlay = true) }
}
