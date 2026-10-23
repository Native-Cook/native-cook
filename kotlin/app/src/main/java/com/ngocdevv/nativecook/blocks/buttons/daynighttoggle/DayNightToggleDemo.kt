package com.ngocdevv.nativecook.blocks.buttons.daynighttoggle

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun DayNightToggleDemo() {
    ComponentStage(background = Color(0xFFE0EDF6)) { DayNightToggle(autoPlay = true, backdrop = true) }
}
