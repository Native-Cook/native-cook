package com.ngocdevv.nativecook.blocks.inputs.dockingpromptcard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun DockingPromptCardDemo() {
    ComponentStage(background = Color(0xFF171717), height = 480.dp) { DockingPromptCard(autoPlay = true) }
}
