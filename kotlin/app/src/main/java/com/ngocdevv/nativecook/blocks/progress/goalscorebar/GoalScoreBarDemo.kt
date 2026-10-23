package com.ngocdevv.nativecook.blocks.progress.goalscorebar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun GoalScoreBarDemo() {
    ComponentStage(background = Color(0xFFE8E8E8), height = 310.dp) { GoalScoreBar(autoPlay = true) }
}
