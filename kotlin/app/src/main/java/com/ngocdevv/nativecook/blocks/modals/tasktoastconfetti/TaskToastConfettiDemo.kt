package com.ngocdevv.nativecook.blocks.modals.tasktoastconfetti

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. The
 * gallery's activity is edge to edge with light icons on both bars, which suits the blue header
 * the block puts under the status bar, so it changes nothing on the window.
 */
@Composable
fun TaskToastConfettiDemo() {
    TaskToastConfetti(autoPlay = true)
}

@Preview
@Composable
private fun TaskToastConfettiDemoPreview() {
    TaskToastConfettiDemo()
}
