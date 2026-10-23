package com.ngocdevv.nativecook.blocks.cards.cutoutwindowcard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun CutoutWindowCardDemo() {
    ComponentStage(background = Color(0xFF0E1411), height = 600.dp) { CutoutWindowCard(autoPlay = true) }
}
