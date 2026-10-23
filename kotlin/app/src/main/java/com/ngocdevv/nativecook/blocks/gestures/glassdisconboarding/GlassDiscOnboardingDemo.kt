package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat

/** The glass onboarding loop, with dark system-bar icons on its light blush field. */
@Composable
fun GlassDiscOnboardingDemo() {
    GlassDiscOnboardingStatusBar()
    GlassDiscOnboarding(autoPlay = true)
}

@Composable
private fun GlassDiscOnboardingStatusBar() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val wasLightStatus = controller.isAppearanceLightStatusBars
        val wasLightNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = wasLightStatus
            controller.isAppearanceLightNavigationBars = wasLightNavigation
        }
    }
}

@Preview
@Composable
private fun GlassDiscOnboardingDemoPreview() {
    GlassDiscOnboardingDemo()
}
