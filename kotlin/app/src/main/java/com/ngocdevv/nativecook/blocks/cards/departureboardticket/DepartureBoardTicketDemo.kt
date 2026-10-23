package com.ngocdevv.nativecook.blocks.cards.departureboardticket

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun DepartureBoardTicketDemo() {
    ComponentStage(background = Color(0xFFF3F3F5), height = 640.dp) { DepartureBoardTicket(autoPlay = true) }
}
