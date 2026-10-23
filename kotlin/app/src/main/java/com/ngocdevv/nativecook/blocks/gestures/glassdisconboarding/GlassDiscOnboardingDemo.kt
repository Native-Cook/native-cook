package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat

@Composable
fun GlassDiscOnboardingDemo() {
    GlassDiscOnboardingSystemBars()
    GlassDiscOnboarding(autoPlay = true)
}

@Composable
private fun GlassDiscOnboardingSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val oldStatus = controller.isAppearanceLightStatusBars
        val oldNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = oldStatus
            controller.isAppearanceLightNavigationBars = oldNavigation
        }
    }
}

@Preview(widthDp = 393, heightDp = 852)
@Composable
private fun GlassDiscOnboardingDemoPreview() {
    GlassDiscOnboarding(autoPlay = false)
}
