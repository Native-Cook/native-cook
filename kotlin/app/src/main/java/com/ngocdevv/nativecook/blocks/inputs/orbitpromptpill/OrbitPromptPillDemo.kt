package com.ngocdevv.nativecook.blocks.inputs.orbitpromptpill

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun OrbitPromptPillDemo() {
    ComponentStage(background = Color(0xFFF4F4F4), height = 380.dp) { OrbitPromptPill(autoPlay = true) }
}
