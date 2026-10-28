package com.ngocdevv.nativecook.blocks.text.inlinepillreveal

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The slowfield welcome screen on the whole screen, revealing its sunrise by itself. */
@Composable
fun InlinePillRevealDemo() {
    InlinePillReveal(autoPlay = true)
}

@Preview
@Composable
private fun InlinePillRevealDemoPreview() {
    InlinePillRevealDemo()
}
