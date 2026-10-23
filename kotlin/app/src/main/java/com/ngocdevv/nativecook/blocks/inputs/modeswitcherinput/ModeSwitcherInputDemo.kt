package com.ngocdevv.nativecook.blocks.inputs.modeswitcherinput

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ModeSwitcherInputDemo() {
    ComponentStage(background = Color(0xFF0B0C12), height = 420.dp) { ModeSwitcherInput(autoPlay = true) }
}
