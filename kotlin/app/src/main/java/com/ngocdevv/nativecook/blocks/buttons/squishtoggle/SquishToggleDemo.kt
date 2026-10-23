package com.ngocdevv.nativecook.blocks.buttons.squishtoggle

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun SquishToggleDemo() {
    ComponentStage(background = Color(0xFFEEEFF4)) { SquishToggle(autoPlay = true) }
}
