package com.ngocdevv.nativecook.blocks.progress.recruitersteps

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun RecruiterStepsDemo() {
    ComponentStage(background = Color(0xFF3F6FAE), height = 240.dp) { RecruiterSteps(autoPlay = true) }
}
