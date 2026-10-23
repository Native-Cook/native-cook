package com.ngocdevv.nativecook.blocks.progress.playbackdial

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun PlaybackDialDemo() {
    ComponentStage(background = Color(0xFF040404), height = 340.dp) { PlaybackDial(autoPlay = true) }
}
