package com.ngocdevv.nativecook.blocks.cards.staybookingcard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun StayBookingCardDemo() {
    ComponentStage(background = Color(0xFFECECEE), height = 520.dp) { StayBookingCard(autoPlay = true) }
}
