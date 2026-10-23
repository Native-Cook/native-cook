package com.ngocdevv.nativecook.blocks.buttons.generatebutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun GenerateButtonDemo() {
    ComponentStage(background = Color(0xFF111111), height = 290.dp) { GenerateButton(active = true) }
}
