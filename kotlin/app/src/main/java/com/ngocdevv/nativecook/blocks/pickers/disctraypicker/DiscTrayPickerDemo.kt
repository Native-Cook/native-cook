package com.ngocdevv.nativecook.blocks.pickers.disctraypicker

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun DiscTrayPickerDemo() {
    ComponentStage(background = Color(0xFFEFEFEF), height = 660.dp) { DiscTrayPicker(autoPlay = true) }
}
