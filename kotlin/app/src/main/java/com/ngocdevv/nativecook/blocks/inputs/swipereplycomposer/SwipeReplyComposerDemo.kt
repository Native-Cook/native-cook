package com.ngocdevv.nativecook.blocks.inputs.swipereplycomposer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun SwipeReplyComposerDemo() {
    ComponentStage(background = Color(0xFF111110), height = 500.dp) { SwipeReplyComposer(autoPlay = true) }
}
