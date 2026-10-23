package com.ngocdevv.nativecook.blocks.inputs.smarttokeninput

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun SmartTokenInputDemo() {
    ComponentStage(background = Color(0xFFCFD4D9), height = 260.dp) { SmartTokenInput(autoPlay = true) }
}
