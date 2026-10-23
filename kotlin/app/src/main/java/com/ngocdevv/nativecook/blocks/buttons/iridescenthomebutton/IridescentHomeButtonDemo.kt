package com.ngocdevv.nativecook.blocks.buttons.iridescenthomebutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun IridescentHomeButtonDemo() {
    ComponentStage(background = Color(0xFF050506)) { IridescentHomeButton(autoPlay = true) }
}
