package com.ngocdevv.nativecook.blocks.buttons.gradientsubscribebutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun GradientSubscribeButtonDemo() {
    ComponentStage(background = Color(0xFF000000)) { GradientSubscribeButton(autoPlay = true) }
}
