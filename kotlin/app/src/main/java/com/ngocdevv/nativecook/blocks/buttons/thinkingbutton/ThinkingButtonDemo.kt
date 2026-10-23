package com.ngocdevv.nativecook.blocks.buttons.thinkingbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ThinkingButtonDemo() {
    ComponentStage(background = Color(0xFF17191B)) { ThinkingButton(autoPlay = true) }
}
