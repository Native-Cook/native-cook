package com.ngocdevv.nativecook.blocks.buttons.applycheckbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun ApplyCheckButtonDemo() {
    ComponentStage(background = Color(0xFFF3F3F3)) { ApplyCheckButton(autoPlay = true) }
}
