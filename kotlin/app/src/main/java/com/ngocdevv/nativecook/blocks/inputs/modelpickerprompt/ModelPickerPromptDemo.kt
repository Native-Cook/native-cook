package com.ngocdevv.nativecook.blocks.inputs.modelpickerprompt

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ModelPickerPromptDemo() {
    ComponentStage(background = Color(0xFF0E0D0C), height = 520.dp) { ModelPickerPrompt(autoPlay = true) }
}
