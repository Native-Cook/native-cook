package com.ngocdevv.nativecook.blocks.progress.timerborderprogress

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun TimerBorderProgressDemo() {
    ComponentStage(background = Color(0xFFE7E7E7), height = 320.dp) { TimerBorderProgress(autoPlay = true) }
}
