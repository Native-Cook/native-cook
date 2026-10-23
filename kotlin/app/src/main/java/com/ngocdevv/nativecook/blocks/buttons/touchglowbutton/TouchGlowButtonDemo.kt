package com.ngocdevv.nativecook.blocks.buttons.touchglowbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun TouchGlowButtonDemo() {
    ComponentStage(background = Color(0xFF03031A)) { TouchGlowButton(autoPlay = true) }
}
