package com.ngocdevv.nativecook.blocks.cards.travelstatstorycard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun TravelStatStoryCardDemo() {
    ComponentStage(background = Color(0xFFE9E8E4), height = 560.dp) { TravelStatStoryCard(autoPlay = true) }
}
