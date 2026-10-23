package com.ngocdevv.nativecook.blocks.animations.expressiveorb

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The original app's `HomeScreen`: a dark scroll view that holds the Orb and its expression
 * picker. Its Activity also hid the status bar, which is done here while the block is open.
 */
@Composable
fun ExpressiveOrbDemo() {
    ExpressiveOrbStatusBar()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GrokOrbPalette.ScreenBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GrokOrbExpressionPicker()
        }
    }
}

/** Hides the status bar while the block is open and shows it again when it closes. */
@Composable
private fun ExpressiveOrbStatusBar() {
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
private fun ExpressiveOrbDemoPreview() {
    ExpressiveOrbDemo()
}
