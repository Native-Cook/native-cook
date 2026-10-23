package com.ngocdevv.nativecook.blocks.pickers.walkradiusdial

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun WalkRadiusDialDemo() {
    ComponentStage(background = Color.White, height = 540.dp) { WalkRadiusDial(autoPlay = true) }
}
