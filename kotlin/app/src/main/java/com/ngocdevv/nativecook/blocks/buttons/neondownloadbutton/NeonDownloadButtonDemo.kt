package com.ngocdevv.nativecook.blocks.buttons.neondownloadbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun NeonDownloadButtonDemo() {
    ComponentStage(background = Color(0xFF313536), height = 260.dp) { NeonDownloadButton(autoPlay = true) }
}
