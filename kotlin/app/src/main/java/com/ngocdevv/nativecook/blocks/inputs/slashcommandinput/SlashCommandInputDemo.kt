package com.ngocdevv.nativecook.blocks.inputs.slashcommandinput

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun SlashCommandInputDemo() {
    ComponentStage(background = Color(0xFF141414), height = 440.dp) { SlashCommandInput(autoPlay = true) }
}
