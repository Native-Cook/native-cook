package com.ngocdevv.nativecook.blocks.cards.flightstatuscard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun FlightStatusCardDemo() {
    ComponentStage(background = Color(0xFFF1F1F1), height = 420.dp) { FlightStatusCard(autoPlay = true) }
}
