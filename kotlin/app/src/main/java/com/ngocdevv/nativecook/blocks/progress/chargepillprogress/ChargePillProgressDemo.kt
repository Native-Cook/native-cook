package com.ngocdevv.nativecook.blocks.progress.chargepillprogress

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ChargePillProgressDemo() {
    ComponentStage(background = Color(0xFFEBEBEB), height = 250.dp) { ChargePillProgress(autoPlay = true) }
}
