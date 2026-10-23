package com.ngocdevv.nativecook.blocks.inputs.holdtospeakinput

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun HoldToSpeakInputDemo() {
    ComponentStage(background = Color(0xFF2B2B2B), height = 300.dp) { HoldToSpeakInput(autoPlay = true) }
}
