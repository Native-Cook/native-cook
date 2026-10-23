package com.ngocdevv.nativecook.blocks.buttons.glassloadingbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun GlassLoadingButtonDemo() {
    ComponentStage(background = Color(0xFF1A1A1A), height = 240.dp) { GlassLoadingButton(autoPlay = true) }
}
