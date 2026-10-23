package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat

@Composable
fun EnvelopeGiftRevealDemo() {
    // The dark linen page uses light system-bar icons.
    val view = LocalView.current
    val activity = LocalActivity.current
    DisposableEffect(view, activity) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (lightStatus != null) controller.isAppearanceLightStatusBars = lightStatus
            if (lightNavigation != null) controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(EnvelopeGiftRevealColors.table)) {
        EnvelopeGiftReveal(autoPlay = true)
    }
}

@Preview
@Composable
private fun EnvelopeGiftRevealDemoPreview() {
    EnvelopeGiftRevealDemo()
}
