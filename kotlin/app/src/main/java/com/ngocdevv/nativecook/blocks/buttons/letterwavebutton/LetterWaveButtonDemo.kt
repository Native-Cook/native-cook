package com.ngocdevv.nativecook.blocks.buttons.letterwavebutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun LetterWaveButtonDemo() {
    ComponentStage(background = Color(0xFFFCFCFC)) { LetterWaveButton(autoPlay = true) }
}
