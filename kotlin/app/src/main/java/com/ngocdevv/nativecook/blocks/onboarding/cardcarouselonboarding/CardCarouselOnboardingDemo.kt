package com.ngocdevv.nativecook.blocks.onboarding.cardcarouselonboarding

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.blocks.onboarding.cardcarouselonboarding.ui.onboarding.OnboardingScreen
import com.ngocdevv.nativecook.blocks.onboarding.cardcarouselonboarding.ui.theme.OnboardingAnimationTheme

/** The original app's root content: `OnboardingAnimationTheme { OnboardingScreen() }`. */
@Composable
fun CardCarouselOnboardingDemo() {
    CardCarouselOnboardingDemoSystemBars()
    OnboardingAnimationTheme {
        OnboardingScreen()
    }
}

/**
 * The screen is light, so both system bars get dark icons while the block is open. The original
 * activity called `enableEdgeToEdge()` with its default styles, where the icons follow the
 * system's light or dark mode instead (light icons on this light screen in dark mode); they are
 * pinned to the screen here. The navigation bar keeps the system's contrast scrim, as those
 * defaults do. The gallery's activity uses light icons without the scrim, so everything is put
 * back when the block closes.
 */
@Composable
private fun CardCarouselOnboardingDemoSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatusBars = controller.isAppearanceLightStatusBars
        val lightNavigationBars = controller.isAppearanceLightNavigationBars
        val contrastEnforced = Build.VERSION.SDK_INT >= 29 && window.isNavigationBarContrastEnforced
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = true
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatusBars
            controller.isAppearanceLightNavigationBars = lightNavigationBars
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = contrastEnforced
        }
    }
}

@Preview
@Composable
private fun CardCarouselOnboardingDemoPreview() {
    CardCarouselOnboardingDemo()
}
