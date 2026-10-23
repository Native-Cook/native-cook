package com.ngocdevv.nativecook.blocks.progress.signalstrengthbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun SignalStrengthBarDemo() {
    ComponentStage(background = Color(0xFF4F6B49), height = 310.dp) { SignalStrengthBar(autoPlay = true) }
}
