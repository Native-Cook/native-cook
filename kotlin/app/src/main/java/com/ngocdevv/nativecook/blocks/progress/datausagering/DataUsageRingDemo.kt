package com.ngocdevv.nativecook.blocks.progress.datausagering

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun DataUsageRingDemo() {
    ComponentStage(background = Color(0xFFF4F4F4), height = 440.dp) { DataUsageRing(autoPlay = true) }
}
