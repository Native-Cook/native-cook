package com.ngocdevv.nativecook.blocks.inputs.morphingmicpill

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun MorphingMicPillDemo() {
    ComponentStage(background = Color(0xFFE6E4DF), height = 480.dp) { MorphingMicPill(autoPlay = true) }
}
