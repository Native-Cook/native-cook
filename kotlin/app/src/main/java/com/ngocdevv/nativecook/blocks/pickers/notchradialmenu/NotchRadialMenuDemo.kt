package com.ngocdevv.nativecook.blocks.pickers.notchradialmenu

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun NotchRadialMenuDemo() {
    ComponentStage(background = Color(0xFF120B24), height = 440.dp) { NotchRadialMenu(autoPlay = true) }
}
