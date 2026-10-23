package com.ngocdevv.nativecook.blocks.pickers.weatherfandial

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun WeatherFanDialDemo() {
    ComponentStage(background = Color(0xFF2C2D32), height = 360.dp) { WeatherFanDial(autoPlay = true) }
}
