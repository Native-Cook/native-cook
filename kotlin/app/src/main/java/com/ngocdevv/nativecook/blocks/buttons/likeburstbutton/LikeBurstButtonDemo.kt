package com.ngocdevv.nativecook.blocks.buttons.likeburstbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun LikeBurstButtonDemo() {
    ComponentStage(background = Color(0xFFFEEEEF)) { LikeBurstButton(autoPlay = true) }
}
