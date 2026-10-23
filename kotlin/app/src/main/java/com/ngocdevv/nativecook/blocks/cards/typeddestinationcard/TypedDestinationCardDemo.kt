package com.ngocdevv.nativecook.blocks.cards.typeddestinationcard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun TypedDestinationCardDemo() {
    ComponentStage(background = Color(0xFFF5F3EE), height = 520.dp) { TypedDestinationCard(autoPlay = true) }
}
