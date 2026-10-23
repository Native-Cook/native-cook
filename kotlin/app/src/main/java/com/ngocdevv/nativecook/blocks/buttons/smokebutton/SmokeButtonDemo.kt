package com.ngocdevv.nativecook.blocks.buttons.smokebutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun SmokeButtonDemo() {
    ComponentStage(background = Color(0xFF050505), height = 260.dp) { SmokeButton() }
}
