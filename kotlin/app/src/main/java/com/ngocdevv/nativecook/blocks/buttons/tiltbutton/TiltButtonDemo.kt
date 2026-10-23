package com.ngocdevv.nativecook.blocks.buttons.tiltbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun TiltButtonDemo() {
    ComponentStage(background = Color.Black) { TiltButton(autoPlay = true) }
}
