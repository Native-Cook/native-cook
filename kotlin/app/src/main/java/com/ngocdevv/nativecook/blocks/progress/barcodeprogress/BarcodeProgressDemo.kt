package com.ngocdevv.nativecook.blocks.progress.barcodeprogress

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun BarcodeProgressDemo() {
    ComponentStage(background = Color(0xFFCCCCCC), height = 280.dp) { BarcodeProgress(autoPlay = true) }
}
