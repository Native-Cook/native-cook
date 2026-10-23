package com.ngocdevv.nativecook.blocks.pickers.branchingpricewheel

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun BranchingPriceWheelDemo() {
    ComponentStage(background = Color(0xFFEEF3F7), height = 580.dp) { BranchingPriceWheel(autoPlay = true) }
}
