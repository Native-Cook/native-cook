package com.ngocdevv.nativecook.shared

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * One component alone on its backdrop, with nothing else on screen — made for screen recording.
 *
 * @param background backdrop colour the component was designed against.
 * @param height height of the box the component is laid out in; taller for a large glow.
 */
@Composable
fun ComponentStage(background: Color, height: Dp = 220.dp, content: @Composable () -> Unit) {
    HideStatusBar()
    Box(
        modifier = Modifier.fillMaxSize().background(background),
        contentAlignment = Alignment.Center,
    ) {
        // The box the component was designed in, so it lays out the same wherever it is shown.
        Box(
            modifier = Modifier.fillMaxWidth().height(height),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun HideStatusBar() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { controller.show(WindowInsetsCompat.Type.statusBars()) }
    }
}
