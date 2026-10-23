package com.ngocdevv.nativecook.blocks.inputs.voicenotecomposer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun VoiceNoteComposerDemo() {
    ComponentStage(background = Color(0xFF161D27), height = 340.dp) { VoiceNoteComposer(autoPlay = true) }
}
