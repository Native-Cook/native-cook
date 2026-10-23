package com.ngocdevv.nativecook.blocks.buttons.glowborderbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun GlowBorderButtonDemo() {
    ComponentStage(background = Color(0xFF121317)) { GlowBorderButton(mode = GlowBorderMode.Always) }
}
