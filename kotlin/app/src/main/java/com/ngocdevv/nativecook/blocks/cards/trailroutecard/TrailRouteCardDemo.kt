package com.ngocdevv.nativecook.blocks.cards.trailroutecard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun TrailRouteCardDemo() {
    ComponentStage(background = Color(0xFFF0F0F0), height = 480.dp) { TrailRouteCard(autoPlay = true) }
}
