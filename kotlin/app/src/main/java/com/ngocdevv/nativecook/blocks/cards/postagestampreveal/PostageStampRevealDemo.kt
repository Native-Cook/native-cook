package com.ngocdevv.nativecook.blocks.cards.postagestampreveal

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun PostageStampRevealDemo() {
    ComponentStage(background = Color(0xFFC8D9F0), height = 520.dp) { PostageStampReveal(autoPlay = true) }
}
