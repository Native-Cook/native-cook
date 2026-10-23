package com.ngocdevv.nativecook.blocks.cards.fannedguidecards

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun FannedGuideCardsDemo() {
    ComponentStage(background = Color(0xFFF0F2EF), height = 520.dp) { FannedGuideCards(autoPlay = true) }
}
