package com.ngocdevv.nativecook.blocks.pickers.durationscrubstrip

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun DurationScrubStripDemo() {
    ComponentStage(background = Color(0xFF161616), height = 200.dp) { DurationScrubStrip(autoPlay = true) }
}
