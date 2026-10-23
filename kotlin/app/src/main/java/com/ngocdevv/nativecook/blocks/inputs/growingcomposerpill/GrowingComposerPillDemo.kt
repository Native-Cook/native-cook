package com.ngocdevv.nativecook.blocks.inputs.growingcomposerpill

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun GrowingComposerPillDemo() {
    ComponentStage(background = Color(0xFFF1EFF8), height = 460.dp) { GrowingComposerPill(autoPlay = true) }
}
