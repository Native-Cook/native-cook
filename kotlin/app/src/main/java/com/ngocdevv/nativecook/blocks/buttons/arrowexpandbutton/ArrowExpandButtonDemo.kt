package com.ngocdevv.nativecook.blocks.buttons.arrowexpandbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ArrowExpandButtonDemo() {
    ComponentStage(background = Color(0xFFEFF6F9)) { ArrowExpandButton(autoPlay = true) }
}
