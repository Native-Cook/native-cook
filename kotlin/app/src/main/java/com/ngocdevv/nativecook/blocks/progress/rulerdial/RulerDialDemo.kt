package com.ngocdevv.nativecook.blocks.progress.rulerdial

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun RulerDialDemo() {
    ComponentStage(background = Color(0xFFF5F5F6), height = 230.dp) { RulerDial(autoPlay = true) }
}
