package com.ngocdevv.nativecook.blocks.cards.resortweathercard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ResortWeatherCardDemo() {
    ComponentStage(background = Color(0xFF060709), height = 660.dp) { ResortWeatherCard(autoPlay = true) }
}
