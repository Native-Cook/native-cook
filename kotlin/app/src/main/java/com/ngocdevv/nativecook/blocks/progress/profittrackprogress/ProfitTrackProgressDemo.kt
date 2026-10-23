package com.ngocdevv.nativecook.blocks.progress.profittrackprogress

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ProfitTrackProgressDemo() {
    ComponentStage(background = Color(0xFFFAFAFA), height = 320.dp) { ProfitTrackProgress(autoPlay = true) }
}
