package com.ngocdevv.nativecook.blocks.pickers.moonorbittimescrubber

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun MoonOrbitTimeScrubberDemo() {
    ComponentStage(background = Color(0xFF05061A), height = 680.dp) { MoonOrbitTimeScrubber(autoPlay = true) }
}
