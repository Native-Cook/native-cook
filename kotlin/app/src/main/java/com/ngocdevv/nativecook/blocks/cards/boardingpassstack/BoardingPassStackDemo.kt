package com.ngocdevv.nativecook.blocks.cards.boardingpassstack

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun BoardingPassStackDemo() {
    ComponentStage(background = Color(0xFF05070C), height = 620.dp) { BoardingPassStack(autoPlay = true) }
}
