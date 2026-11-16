package com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The catalog's demo: the pin's screen playing its loop; a touch takes over (a session runs 24x fast so it can be watched). */
@Composable
fun FlipFocusBadgeDemo() {
    FlipFocusBadge(autoPlay = true)
}

@Preview
@Composable
private fun FlipFocusBadgeDemoPreview() {
    FlipFocusBadgeDemo()
}
