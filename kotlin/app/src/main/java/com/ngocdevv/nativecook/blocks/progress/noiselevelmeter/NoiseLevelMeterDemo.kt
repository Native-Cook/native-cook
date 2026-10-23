package com.ngocdevv.nativecook.blocks.progress.noiselevelmeter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun NoiseLevelMeterDemo() {
    ComponentStage(background = Color(0xFFCED2DA), height = 270.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NoiseLevelMeter(autoPlay = true, level = 40f)
            NoiseLevelMeter(autoPlay = true, level = 60f)
        }
    }
}
