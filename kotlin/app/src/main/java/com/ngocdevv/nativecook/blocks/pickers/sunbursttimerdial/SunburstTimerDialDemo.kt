package com.ngocdevv.nativecook.blocks.pickers.sunbursttimerdial

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun SunburstTimerDialDemo() {
    ComponentStage(background = Color(0xFFF5F4F1), height = 380.dp) { SunburstTimerDial(autoPlay = true) }
}
