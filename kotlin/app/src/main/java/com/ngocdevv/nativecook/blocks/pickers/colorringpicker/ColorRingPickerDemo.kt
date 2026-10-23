package com.ngocdevv.nativecook.blocks.pickers.colorringpicker

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ColorRingPickerDemo() {
    ComponentStage(background = Color(0xFF121417), height = 460.dp) { ColorRingPicker(autoPlay = true) }
}
