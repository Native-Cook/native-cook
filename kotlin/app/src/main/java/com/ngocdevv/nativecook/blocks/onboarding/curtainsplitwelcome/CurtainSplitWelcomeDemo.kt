package com.ngocdevv.nativecook.blocks.onboarding.curtainsplitwelcome

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

/** The welcome screen on the whole screen, turning its scenes by itself, under a hidden status bar. */
@Composable
fun CurtainSplitWelcomeDemo() {
    CurtainSplitWelcomeStatusBar()
    Box(Modifier.fillMaxSize().background(Color(0xFF1A1916))) {
        CurtainSplitWelcome(autoPlay = true)
    }
}

/** Hides the status bar while the block is open and shows it again when it closes. */
@Composable
private fun CurtainSplitWelcomeStatusBar() {
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
private fun CurtainSplitWelcomeDemoPreview() {
    CurtainSplitWelcomeDemo()
}
