package com.ngocdevv.nativecook.blocks.modals.islandtoaststack

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

/** The island and its toasts on the whole screen, playing through by themselves. */
@Composable
fun IslandToastStackDemo() {
    IslandToastStackStatusBar()
    Box(Modifier.fillMaxSize().background(Color(0xFF07080E))) {
        IslandToastStack(autoPlay = true)
    }
}

/** Light status bar icons over the dark wallpaper while the block is open; the old ones after. */
@Composable
private fun IslandToastStackStatusBar() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val light = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = light }
    }
}

@Preview
@Composable
private fun IslandToastStackDemoPreview() {
    IslandToastStackDemo()
}
