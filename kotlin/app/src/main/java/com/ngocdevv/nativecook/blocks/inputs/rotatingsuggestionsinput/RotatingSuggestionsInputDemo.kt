package com.ngocdevv.nativecook.blocks.inputs.rotatingsuggestionsinput

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun RotatingSuggestionsInputDemo() {
    ComponentStage(background = Color(0xFFE4E4E9), height = 440.dp) { RotatingSuggestionsInput(autoPlay = true) }
}
