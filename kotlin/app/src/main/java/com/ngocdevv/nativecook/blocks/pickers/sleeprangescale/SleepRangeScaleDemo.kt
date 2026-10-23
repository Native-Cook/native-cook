package com.ngocdevv.nativecook.blocks.pickers.sleeprangescale

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun SleepRangeScaleDemo() {
    ComponentStage(background = Color(0xFFCFCDD8), height = 460.dp) { SleepRangeScale(autoPlay = true) }
}
