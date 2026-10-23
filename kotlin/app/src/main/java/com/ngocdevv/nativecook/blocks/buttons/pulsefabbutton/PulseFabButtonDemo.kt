package com.ngocdevv.nativecook.blocks.buttons.pulsefabbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun PulseFabButtonDemo() {
    ComponentStage(background = Color(0xFFF4F9FF)) { PulseFabButton(autoPlay = true) }
}
