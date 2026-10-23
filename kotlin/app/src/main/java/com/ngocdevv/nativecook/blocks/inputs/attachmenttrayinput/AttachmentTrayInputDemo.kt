package com.ngocdevv.nativecook.blocks.inputs.attachmenttrayinput

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun AttachmentTrayInputDemo() {
    ComponentStage(background = Color.White, height = 400.dp) { AttachmentTrayInput(autoPlay = true) }
}
