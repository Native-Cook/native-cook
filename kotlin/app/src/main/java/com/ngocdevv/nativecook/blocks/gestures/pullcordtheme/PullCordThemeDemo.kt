package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** The gallery entry point: an edge-to-edge reading page playing the reference's twelve-second loop. */
@Composable
fun PullCordThemeDemo() {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    if (window != null) {
        DisposableEffect(window, view) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
            onDispose { controller.show(WindowInsetsCompat.Type.statusBars()) }
        }
    }
    PullCordTheme(autoPlay = true)
}

@Preview
@Composable
private fun PullCordThemeDemoPreview() {
    PullCordThemeDemo()
}
