package com.ngocdevv.nativecook.blocks.inputs.thinkingframesearch

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ThinkingFrameSearchDemo() {
    ComponentStage(background = Color(0xFFE8E8E8), height = 340.dp) { ThinkingFrameSearch(autoPlay = true) }
}
