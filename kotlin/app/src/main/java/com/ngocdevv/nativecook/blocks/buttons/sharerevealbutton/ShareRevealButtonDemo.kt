package com.ngocdevv.nativecook.blocks.buttons.sharerevealbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ShareRevealButtonDemo() {
    ComponentStage(background = Color(0xFF080720)) { ShareRevealButton(autoPlay = true) }
}
