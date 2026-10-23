package com.ngocdevv.nativecook.blocks.pickers.datelensstrip

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun DateLensStripDemo() {
    ComponentStage(background = Color(0xFFE6E6E8), height = 220.dp) { DateLensStrip(autoPlay = true) }
}
