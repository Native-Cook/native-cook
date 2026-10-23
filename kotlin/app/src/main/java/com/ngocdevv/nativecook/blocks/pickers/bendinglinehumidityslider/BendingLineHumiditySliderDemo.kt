package com.ngocdevv.nativecook.blocks.pickers.bendinglinehumidityslider

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun BendingLineHumiditySliderDemo() {
    ComponentStage(background = Color(0xFF070A26), height = 640.dp) { BendingLineHumiditySlider(autoPlay = true) }
}
