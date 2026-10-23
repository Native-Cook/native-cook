package com.ngocdevv.nativecook.blocks.progress.spendingorbit

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun SpendingOrbitDemo() {
    ComponentStage(background = Color(0xFF191919), height = 370.dp) { SpendingOrbit(autoPlay = true) }
}
