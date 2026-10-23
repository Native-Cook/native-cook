package com.ngocdevv.nativecook.blocks.progress.chargewavecircle

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ChargeWaveCircleDemo() {
    ComponentStage(background = Color(0xFF141A16), height = 470.dp) { ChargeWaveCircle(autoPlay = true) }
}
