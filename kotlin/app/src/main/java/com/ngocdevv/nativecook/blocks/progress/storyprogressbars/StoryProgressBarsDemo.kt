package com.ngocdevv.nativecook.blocks.progress.storyprogressbars

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun StoryProgressBarsDemo() {
    ComponentStage(background = Color(0xFF101010), height = 400.dp) { StoryProgressBars(autoPlay = true) }
}
