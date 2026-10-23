package com.ngocdevv.nativecook.blocks.progress.missionglowprogress

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun MissionGlowProgressDemo() {
    ComponentStage(background = Color(0xFF08060A), height = 300.dp) { MissionGlowProgress(autoPlay = true) }
}
