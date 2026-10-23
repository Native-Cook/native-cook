package com.ngocdevv.nativecook.blocks.buttons.payscanbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun PayScanButtonDemo() {
    ComponentStage(background = Color(0xFFE7FDFF)) { PayScanButton(autoPlay = true) }
}
