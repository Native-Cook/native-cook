package com.ngocdevv.nativecook.blocks.inputs.rainbowoutlinesearch

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun RainbowOutlineSearchDemo() {
    ComponentStage(background = Color(0xFFF3F3F3), height = 240.dp) { RainbowOutlineSearch(autoPlay = true) }
}
