package com.ngocdevv.nativecook.blocks.buttons.ordertruckbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun OrderTruckButtonDemo() {
    ComponentStage(background = Color(0xFFE1EBF7)) { OrderTruckButton(autoPlay = true) }
}
