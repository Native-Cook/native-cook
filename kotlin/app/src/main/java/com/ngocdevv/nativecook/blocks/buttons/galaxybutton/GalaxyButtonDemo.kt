package com.ngocdevv.nativecook.blocks.buttons.galaxybutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun GalaxyButtonDemo() {
    ComponentStage(background = Color(0xFF04000F), height = 250.dp) { GalaxyButton(autoPlay = true) }
}
