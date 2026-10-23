package com.ngocdevv.nativecook.blocks.cards.routeprogresscard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun RouteProgressCardDemo() {
    ComponentStage(background = Color(0xFFE8E8EA), height = 400.dp) { RouteProgressCard(autoPlay = true) }
}
