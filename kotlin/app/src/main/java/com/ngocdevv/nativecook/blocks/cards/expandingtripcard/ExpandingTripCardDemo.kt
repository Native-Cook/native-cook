package com.ngocdevv.nativecook.blocks.cards.expandingtripcard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ExpandingTripCardDemo() {
    ComponentStage(background = Color(0xFFE9EAEC), height = 680.dp) { ExpandingTripCard(autoPlay = true) }
}
