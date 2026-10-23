package com.ngocdevv.nativecook.blocks.inputs.jellyattachcapsule

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun JellyAttachCapsuleDemo() {
    ComponentStage(background = Color(0xFFEEEAFE), height = 240.dp) { JellyAttachCapsule(autoPlay = true) }
}
