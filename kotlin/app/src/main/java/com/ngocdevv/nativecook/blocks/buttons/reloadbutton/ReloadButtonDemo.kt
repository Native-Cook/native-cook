package com.ngocdevv.nativecook.blocks.buttons.reloadbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ReloadButtonDemo() {
    ComponentStage(background = Color(0xFFF7F7F7)) { ReloadButton(autoPlay = true) }
}
