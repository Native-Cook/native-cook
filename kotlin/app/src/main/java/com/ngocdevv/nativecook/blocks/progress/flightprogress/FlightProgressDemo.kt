package com.ngocdevv.nativecook.blocks.progress.flightprogress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun FlightProgressDemo() {
    ComponentStage(background = Color(0xFF000000), height = 440.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            FlightProgress(autoPlay = true)
            FlightProgress(autoPlay = true, tint = FlightTint.Orange)
        }
    }
}
