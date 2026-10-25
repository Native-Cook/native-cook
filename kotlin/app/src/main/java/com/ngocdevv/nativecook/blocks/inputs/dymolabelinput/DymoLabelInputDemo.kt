package com.ngocdevv.nativecook.blocks.inputs.dymolabelinput

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** The label maker on the whole screen, labelling jars by itself, under a hidden status bar (as in React Native). */
@Composable
fun DymoLabelInputDemo() {
    DymoLabelInputStatusBar()
    DymoLabelInput(autoPlay = true)
}

/** Hides the status bar while the block is open and shows it again when it closes. */
@Composable
private fun DymoLabelInputStatusBar() {
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
private fun DymoLabelInputDemoPreview() {
    DymoLabelInputDemo()
}
