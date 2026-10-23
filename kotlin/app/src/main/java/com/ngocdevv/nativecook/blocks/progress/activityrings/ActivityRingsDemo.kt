package com.ngocdevv.nativecook.blocks.progress.activityrings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ActivityRingsDemo() {
    ComponentStage(background = Color(0xFF4B4B4B), height = 280.dp) { ActivityRings(autoPlay = true) }
}
