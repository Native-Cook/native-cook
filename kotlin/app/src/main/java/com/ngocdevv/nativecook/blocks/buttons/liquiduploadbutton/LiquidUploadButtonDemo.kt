package com.ngocdevv.nativecook.blocks.buttons.liquiduploadbutton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun LiquidUploadButtonDemo() {
    ComponentStage(background = Color(0xFFE1E9F7), height = 250.dp) {
        // The reference shows the four variants together, starting one after another.
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LiquidUploadButton(autoPlay = true)
                LiquidUploadButton(shape = UploadShape.Round, tone = UploadTone.Dark, autoPlay = true, autoPlayDelayMillis = 900)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LiquidUploadButton(shape = UploadShape.Round, tone = UploadTone.Light, autoPlay = true, autoPlayDelayMillis = 1800)
                LiquidUploadButton(tone = UploadTone.Dark, autoPlay = true, autoPlayDelayMillis = 2700)
            }
        }
    }
}
