package com.ngocdevv.nativecook.blocks.buttons.tallycounterbutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun TallyCounterButtonDemo() {
    ComponentStage(background = Color(0xFF2C2C2C), height = 240.dp) { TallyCounterButton(autoPlay = true) }
}
