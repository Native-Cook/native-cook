package com.ngocdevv.nativecook.blocks.progress.projectprogresscard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ProjectProgressCardDemo() {
    ComponentStage(background = Color(0xFFF5F7F8), height = 330.dp) { ProjectProgressCard(autoPlay = true) }
}
