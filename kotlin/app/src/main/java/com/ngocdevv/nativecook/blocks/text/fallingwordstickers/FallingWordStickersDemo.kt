package com.ngocdevv.nativecook.blocks.text.fallingwordstickers

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * The block fills the screen and keeps the status bar, so it is shown without a stage. The screen
 * is light, so the block itself switches the bar icons to dark while it is open.
 */
@Composable
fun FallingWordStickersDemo() {
    FallingWordStickers(autoPlay = true)
}

@Preview
@Composable
private fun FallingWordStickersDemoPreview() {
    FallingWordStickersDemo()
}
