package com.ngocdevv.nativecook.blocks.progress.milestonestepper

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun MilestoneStepperDemo() {
    ComponentStage(background = Color(0xFFE7E6E9), height = 340.dp) { MilestoneStepper(autoPlay = true) }
}
