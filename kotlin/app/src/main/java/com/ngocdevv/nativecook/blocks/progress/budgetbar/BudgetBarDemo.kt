package com.ngocdevv.nativecook.blocks.progress.budgetbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun BudgetBarDemo() {
    ComponentStage(background = Color(0xFFFDFDFD), height = 360.dp) { BudgetBar(autoPlay = true) }
}
