package com.ngocdevv.nativecook.blocks.progress.powerratesegments

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun PowerRateSegmentsDemo() {
    ComponentStage(background = Color(0xFF020403), height = 384.dp) { PowerRateSegments(autoPlay = true) }
}
