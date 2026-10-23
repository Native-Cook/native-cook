package com.ngocdevv.nativecook.blocks.buttons.submitconfettibutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun SubmitConfettiButtonDemo() {
    ComponentStage(background = Color(0xFFEFF4FC), height = 280.dp) { SubmitConfettiButton(autoPlay = true) }
}
