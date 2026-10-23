package com.ngocdevv.nativecook.blocks.progress.ridertrackprogress

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun RiderTrackProgressDemo() {
    ComponentStage(background = Color(0xFF02040D), height = 320.dp) { RiderTrackProgress(autoPlay = true) }
}
