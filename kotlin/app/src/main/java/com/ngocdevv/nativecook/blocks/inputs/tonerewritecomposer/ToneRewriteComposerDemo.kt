package com.ngocdevv.nativecook.blocks.inputs.tonerewritecomposer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ToneRewriteComposerDemo() {
    ComponentStage(background = Color(0xFFD3DAEB), height = 420.dp) { ToneRewriteComposer(autoPlay = true) }
}
