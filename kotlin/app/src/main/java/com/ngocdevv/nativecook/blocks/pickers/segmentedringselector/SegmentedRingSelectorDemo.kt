package com.ngocdevv.nativecook.blocks.pickers.segmentedringselector

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun SegmentedRingSelectorDemo() {
    ComponentStage(background = Color(0xFF22343A), height = 380.dp) { SegmentedRingSelector(autoPlay = true) }
}
