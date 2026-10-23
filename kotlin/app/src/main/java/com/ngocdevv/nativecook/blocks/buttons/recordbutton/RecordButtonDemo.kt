package com.ngocdevv.nativecook.blocks.buttons.recordbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun RecordButtonDemo() {
    ComponentStage(background = Color(0xFF050505), height = 280.dp) { RecordButton(autoPlay = true) }
}
