package com.ngocdevv.nativecook.blocks.playground.zensandgarden

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

/** The sand garden on the whole screen, raking, ringing and smoothing itself, under a hidden status bar. */
@Composable
fun ZenSandGardenDemo() {
    ZenSandGardenStatusBar()
    Box(Modifier.fillMaxSize().background(Color(0xFFD9CDB8))) {
        ZenSandGarden(autoPlay = true)
    }
}

/** Hides the status bar while the block is open and shows it again when it closes. */
@Composable
private fun ZenSandGardenStatusBar() {
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
private fun ZenSandGardenDemoPreview() {
    ZenSandGardenDemo()
}
