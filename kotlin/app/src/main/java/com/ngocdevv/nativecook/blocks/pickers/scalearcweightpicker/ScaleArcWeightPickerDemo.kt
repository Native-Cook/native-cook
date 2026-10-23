package com.ngocdevv.nativecook.blocks.pickers.scalearcweightpicker

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ScaleArcWeightPickerDemo() {
    ComponentStage(background = Color(0xFFF4F3FA), height = 440.dp) { ScaleArcWeightPicker(autoPlay = true) }
}
