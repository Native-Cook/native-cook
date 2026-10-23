package com.ngocdevv.nativecook.blocks.progress.waterintakefill

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun WaterIntakeFillDemo() {
    ComponentStage(background = Color(0xFFB9CF7A), height = 480.dp) { WaterIntakeFill(autoPlay = true) }
}
