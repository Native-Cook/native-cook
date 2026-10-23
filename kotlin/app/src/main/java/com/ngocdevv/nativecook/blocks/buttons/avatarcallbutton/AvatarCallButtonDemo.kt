package com.ngocdevv.nativecook.blocks.buttons.avatarcallbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun AvatarCallButtonDemo() {
    ComponentStage(background = Color(0xFFEEEEEE)) { AvatarCallButton(autoPlay = true) }
}
