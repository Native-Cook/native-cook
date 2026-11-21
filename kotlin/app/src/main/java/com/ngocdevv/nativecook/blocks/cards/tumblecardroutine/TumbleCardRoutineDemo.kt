package com.ngocdevv.nativecook.blocks.cards.tumblecardroutine

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun TumbleCardRoutineDemo() {
    TumbleCardRoutine(autoPlay = true)
}

@Preview
@Composable
private fun TumbleCardRoutineDemoPreview() {
    TumbleCardRoutineDemo()
}
