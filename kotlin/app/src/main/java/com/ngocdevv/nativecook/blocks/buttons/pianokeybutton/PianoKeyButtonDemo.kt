package com.ngocdevv.nativecook.blocks.buttons.pianokeybutton

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen edge to edge and keeps the status bar (its header sits under it), so
 * it is shown without a stage. The gallery's activity already draws light icons on both bars,
 * which is what the dark lacquer needs.
 */
@Composable
fun PianoKeyButtonDemo() {
    PianoKeyButton(autoPlay = true)
}

@Preview
@Composable
private fun PianoKeyButtonDemoPreview() {
    PianoKeyButtonDemo()
}
