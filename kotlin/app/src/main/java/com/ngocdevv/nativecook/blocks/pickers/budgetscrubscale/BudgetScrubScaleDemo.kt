package com.ngocdevv.nativecook.blocks.pickers.budgetscrubscale

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun BudgetScrubScaleDemo() {
    ComponentStage(background = Color(0xFFE6E6EC), height = 520.dp) { BudgetScrubScale(autoPlay = true) }
}
