package com.ngocdevv.nativecook.blocks.cards.travelpassstack

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun TravelPassStackDemo() {
    ComponentStage(background = Color(0xFF1D1E30), height = 660.dp) { TravelPassStack(autoPlay = true) }
}
