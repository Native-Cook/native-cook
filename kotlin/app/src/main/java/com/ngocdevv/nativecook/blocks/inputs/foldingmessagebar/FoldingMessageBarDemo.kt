package com.ngocdevv.nativecook.blocks.inputs.foldingmessagebar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun FoldingMessageBarDemo() {
    ComponentStage(background = Color(0xFF00080A), height = 390.dp) { FoldingMessageBar(autoPlay = true) }
}
