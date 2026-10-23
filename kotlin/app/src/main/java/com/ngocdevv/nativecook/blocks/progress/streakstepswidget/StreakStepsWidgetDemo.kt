package com.ngocdevv.nativecook.blocks.progress.streakstepswidget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun StreakStepsWidgetDemo() {
    ComponentStage(background = Color(0xFF141414), height = 390.dp) { StreakStepsWidget(autoPlay = true) }
}
