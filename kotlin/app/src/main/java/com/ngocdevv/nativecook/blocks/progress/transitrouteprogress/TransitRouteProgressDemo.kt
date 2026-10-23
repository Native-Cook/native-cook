package com.ngocdevv.nativecook.blocks.progress.transitrouteprogress

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun TransitRouteProgressDemo() {
    ComponentStage(background = Color(0xFFE7E7E7), height = 250.dp) { TransitRouteProgress(autoPlay = true) }
}
