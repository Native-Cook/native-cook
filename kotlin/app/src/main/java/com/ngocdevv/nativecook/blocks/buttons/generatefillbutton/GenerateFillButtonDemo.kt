package com.ngocdevv.nativecook.blocks.buttons.generatefillbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun GenerateFillButtonDemo() {
    ComponentStage(background = Color(0xFFFFFFFF)) { GenerateFillButton(autoPlay = true) }
}
