package com.ngocdevv.nativecook.blocks.cards.stampedticketpile

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun StampedTicketPileDemo() {
    ComponentStage(background = Color(0xFFF7F1F1), height = 560.dp) { StampedTicketPile(autoPlay = true) }
}
