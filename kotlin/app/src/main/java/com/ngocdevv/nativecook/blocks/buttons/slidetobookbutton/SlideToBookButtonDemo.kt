package com.ngocdevv.nativecook.blocks.buttons.slidetobookbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun SlideToBookButtonDemo() {
    ComponentStage(background = Color(0xFFFAFAFA)) { SlideToBookButton(autoPlay = true) }
}
