package com.ngocdevv.nativecook.blocks.gestures.pullleverrefresh

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

@Composable
fun PullLeverRefreshDemo() {
    if (!LocalInspectionMode.current) PullLeverRefreshStatusBar()
    PullLeverRefresh(autoPlay = true)
}

/** Mirrors the source demo's hidden status bar without changing navigation-bar visibility. */
@Composable
private fun PullLeverRefreshStatusBar() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val type = WindowInsetsCompat.Type.statusBars()
        val wasVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(type) ?: true
        val controller = WindowCompat.getInsetsController(window, view)
        controller.hide(type)
        onDispose {
            if (wasVisible) controller.show(type) else controller.hide(type)
        }
    }
}

@Preview
@Composable
private fun PullLeverRefreshDemoPreview() {
    PullLeverRefreshDemo()
}
