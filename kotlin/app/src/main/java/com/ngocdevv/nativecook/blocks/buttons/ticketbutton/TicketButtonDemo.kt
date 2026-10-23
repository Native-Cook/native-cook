package com.ngocdevv.nativecook.blocks.buttons.ticketbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun TicketButtonDemo() {
    ComponentStage(background = Color(0xFF190E1C)) { TicketButton(autoPlay = true) }
}
