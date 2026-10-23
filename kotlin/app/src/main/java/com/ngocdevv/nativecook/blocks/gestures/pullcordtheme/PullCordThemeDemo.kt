package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The gallery's demo: the block on its own, playing its 12 s loop. */
@Composable
fun PullCordThemeDemo() {
    PullCordTheme(autoPlay = true)
}

@Preview
@Composable
private fun PullCordThemeDemoPreview() {
    PullCordThemeDemo()
}
