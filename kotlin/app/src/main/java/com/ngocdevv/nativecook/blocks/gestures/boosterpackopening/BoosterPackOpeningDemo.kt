package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The block on the whole screen, playing by itself: the pack's scene from edge to edge over a dark
 * backdrop, with the status bar hidden while it is open.
 */
@Composable
fun BoosterPackOpeningDemo() {
    BoosterPackOpeningStatusBar()
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF07090D))) {
        BoosterPackOpening(autoPlay = true)
    }
}

/** Hides the status bar while the block is open and brings it back when it closes. */
@Composable
private fun BoosterPackOpeningStatusBar() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val behavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = behavior
        }
    }
}

@Preview
@Composable
private fun BoosterPackOpeningDemoPreview() {
    BoosterPackOpeningDemo()
}
