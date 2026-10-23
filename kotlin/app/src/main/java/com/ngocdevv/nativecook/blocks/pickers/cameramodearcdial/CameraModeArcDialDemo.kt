package com.ngocdevv.nativecook.blocks.pickers.cameramodearcdial

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun CameraModeArcDialDemo() {
    ComponentStage(background = Color.Black, height = 360.dp) { CameraModeArcDial(autoPlay = true) }
}
