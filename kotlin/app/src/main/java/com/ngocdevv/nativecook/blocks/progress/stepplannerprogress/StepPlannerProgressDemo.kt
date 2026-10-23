package com.ngocdevv.nativecook.blocks.progress.stepplannerprogress

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun StepPlannerProgressDemo() {
    ComponentStage(background = Color(0xFF0A0A0A), height = 446.dp) { StepPlannerProgress(autoPlay = true) }
}
