package com.ngocdevv.nativecook.blocks.buttons.depthbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun DepthButtonDemo() {
    ComponentStage(background = Color(0xFFF5E9D3)) { DepthButtonGroup(autoPlay = true) }
}
