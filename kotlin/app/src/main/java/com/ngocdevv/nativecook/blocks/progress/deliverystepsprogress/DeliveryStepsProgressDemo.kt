package com.ngocdevv.nativecook.blocks.progress.deliverystepsprogress

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun DeliveryStepsProgressDemo() {
    ComponentStage(background = Color(0xFFFDFDFD), height = 280.dp) { DeliveryStepsProgress(autoPlay = true) }
}
