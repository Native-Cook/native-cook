package com.ngocdevv.nativecook.blocks.cards.trailphotofancard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun TrailPhotoFanCardDemo() {
    ComponentStage(background = Color(0xFF1F9FC4), height = 620.dp) { TrailPhotoFanCard(autoPlay = true) }
}
