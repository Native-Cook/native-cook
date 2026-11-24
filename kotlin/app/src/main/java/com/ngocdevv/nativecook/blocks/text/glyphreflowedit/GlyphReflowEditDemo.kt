package com.ngocdevv.nativecook.blocks.text.glyphreflowedit

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ngocdevv.nativecook.shared.BlockSystemBars

/** The block fills the screen edge to edge; the page is light, so the system bars get dark icons. */
@Composable
fun GlyphReflowEditDemo() {
    BlockSystemBars(darkIcons = true)
    GlyphReflowEdit(autoPlay = true)
}

@Preview
@Composable
private fun GlyphReflowEditDemoPreview() {
    GlyphReflowEditDemo()
}
