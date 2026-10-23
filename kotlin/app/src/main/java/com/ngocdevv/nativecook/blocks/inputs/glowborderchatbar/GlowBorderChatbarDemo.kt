package com.ngocdevv.nativecook.blocks.inputs.glowborderchatbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun GlowBorderChatbarDemo() {
    ComponentStage(background = Color.Black, height = 300.dp) { GlowBorderChatbar(autoPlay = true) }
}
