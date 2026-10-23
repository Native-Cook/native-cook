package com.ngocdevv.nativecook.blocks.pickers.gooeyradialpicker

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun GooeyRadialPickerDemo() {
    ComponentStage(background = Color(0xFFE9EEF2), height = 440.dp) { GooeyRadialPicker(autoPlay = true) }
}
