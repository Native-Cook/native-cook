package com.ngocdevv.nativecook.blocks.gestures.parkassistscrub

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

@Composable
fun ParkAssistScrubDemo() {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    DisposableEffect(window,view) {
        val controller = window?.let { WindowCompat.getInsetsController(it,view) }
        val wasLight = controller?.isAppearanceLightStatusBars
        controller?.show(WindowInsetsCompat.Type.statusBars())
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (controller != null && wasLight != null) controller.isAppearanceLightStatusBars = wasLight }
    }
    ParkAssistScrub(autoPlay=true)
}
