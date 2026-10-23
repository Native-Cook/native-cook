package com.ngocdevv.nativecook.blocks.buttons.pricingsegmentedbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun PricingSegmentedButtonDemo() {
    ComponentStage(background = Color(0xFFFDFDFD)) { PricingSegmentedButton(autoPlay = true) }
}
