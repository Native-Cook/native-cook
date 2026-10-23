package com.ngocdevv.nativecook.blocks.cards.risingtourstack

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun RisingTourStackDemo() {
    ComponentStage(background = Color(0xFFEBEBEB), height = 680.dp) { RisingTourStack(autoPlay = true) }
}
