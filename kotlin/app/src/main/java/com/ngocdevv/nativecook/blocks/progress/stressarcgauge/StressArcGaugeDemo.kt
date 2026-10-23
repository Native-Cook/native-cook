package com.ngocdevv.nativecook.blocks.progress.stressarcgauge

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun StressArcGaugeDemo() {
    ComponentStage(background = Color(0xFFFFFFFF), height = 290.dp) { StressArcGauge(autoPlay = true) }
}
