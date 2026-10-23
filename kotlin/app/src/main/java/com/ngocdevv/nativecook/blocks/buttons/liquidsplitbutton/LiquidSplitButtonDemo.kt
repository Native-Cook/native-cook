package com.ngocdevv.nativecook.blocks.buttons.liquidsplitbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun LiquidSplitButtonDemo() {
    ComponentStage(background = Color(0xFFACB0B6)) { LiquidSplitButton(autoPlay = true) }
}
