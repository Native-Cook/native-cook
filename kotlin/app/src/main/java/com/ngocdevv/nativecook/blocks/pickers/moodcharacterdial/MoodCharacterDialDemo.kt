package com.ngocdevv.nativecook.blocks.pickers.moodcharacterdial

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun MoodCharacterDialDemo() {
    ComponentStage(background = Color(0xFF141B1C), height = 660.dp) { MoodCharacterDial(autoPlay = true) }
}
