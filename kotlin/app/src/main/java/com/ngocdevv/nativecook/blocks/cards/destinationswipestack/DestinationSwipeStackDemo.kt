package com.ngocdevv.nativecook.blocks.cards.destinationswipestack

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun DestinationSwipeStackDemo() {
    ComponentStage(background = Color(0xFFF4F4F7), height = 620.dp) { DestinationSwipeStack(autoPlay = true) }
}
