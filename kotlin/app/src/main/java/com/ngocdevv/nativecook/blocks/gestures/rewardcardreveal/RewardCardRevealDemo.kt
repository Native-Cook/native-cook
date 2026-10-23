package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat

/** The original app's root content: `HoloDexApp()`, the card gallery and the card it opens. */
@Composable
fun RewardCardRevealDemo() {
    RewardCardRevealSystemBars()
    HoloDexApp()
}

/**
 * Both screens sit on a light sheet, so the original activity asked for dark system bar icons
 * (`SystemBarStyle.light`). The gallery's activity uses light icons, so they are switched while
 * the block is open and put back when it closes.
 */
@Composable
private fun RewardCardRevealSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatusBars = controller.isAppearanceLightStatusBars
        val lightNavigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatusBars
            controller.isAppearanceLightNavigationBars = lightNavigationBars
        }
    }
}

@Preview
@Composable
private fun RewardCardRevealDemoPreview() {
    RewardCardRevealDemo()
}
