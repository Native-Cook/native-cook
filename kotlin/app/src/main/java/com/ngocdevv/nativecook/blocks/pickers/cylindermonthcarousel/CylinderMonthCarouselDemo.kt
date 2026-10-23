package com.ngocdevv.nativecook.blocks.pickers.cylindermonthcarousel

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun CylinderMonthCarouselDemo() {
    ComponentStage(background = Color(0xFF1B1B1D), height = 540.dp) { CylinderMonthCarousel(autoPlay = true) }
}
