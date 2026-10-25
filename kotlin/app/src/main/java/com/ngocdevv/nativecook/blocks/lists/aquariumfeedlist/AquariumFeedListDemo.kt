package com.ngocdevv.nativecook.blocks.lists.aquariumfeedlist

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** The reef log on the whole screen, scrolling and feeding by itself, under a hidden status bar (as in React Native). */
@Composable
fun AquariumFeedListDemo() {
    AquariumFeedListStatusBar()
    AquariumFeedList(autoPlay = true)
}

/** Hides the status bar while the block is open and shows it again when it closes. */
@Composable
private fun AquariumFeedListStatusBar() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { controller.show(WindowInsetsCompat.Type.statusBars()) }
    }
}

@Preview
@Composable
private fun AquariumFeedListDemoPreview() {
    AquariumFeedListDemo()
}
