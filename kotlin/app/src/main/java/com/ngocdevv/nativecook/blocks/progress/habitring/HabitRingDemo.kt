package com.ngocdevv.nativecook.blocks.progress.habitring

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun HabitRingDemo() {
    ComponentStage(background = Color(0xFF25272C), height = 450.dp) { HabitRing(autoPlay = true) }
}
