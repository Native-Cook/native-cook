package com.ngocdevv.nativecook.blocks.cards.frosteddestinationcarousel

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun FrostedDestinationCarouselDemo() {
    ComponentStage(background = Color(0xFFE6EAF0), height = 440.dp) { FrostedDestinationCarousel(autoPlay = true) }
}
