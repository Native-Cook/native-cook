package com.ngocdevv.nativecook.blocks.progress.batterycapsules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun BatteryCapsulesDemo() {
    ComponentStage(background = Color(0xFF6C8AB3), height = 250.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BatteryCapsules(autoPlay = true)
            BatteryCapsules(autoPlay = true, variant = BatteryVariant.Dark)
        }
    }
}
