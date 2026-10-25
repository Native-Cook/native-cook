package com.ngocdevv.nativecook.blocks.cards.perforatedcoupontear

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The block fills the screen edge to edge and keeps its content clear of the cutout itself. As in
 * the React Native demo the status bar is hidden; the navigation bar gets dark icons on the paper.
 */
@Composable
fun PerforatedCouponTearDemo() {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    if (window != null) {
        DisposableEffect(window, view) {
            val controller = WindowCompat.getInsetsController(window, view)
            val lightNav = controller.isAppearanceLightNavigationBars
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.isAppearanceLightNavigationBars = true
            onDispose {
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.isAppearanceLightNavigationBars = lightNav
            }
        }
    }
    PerforatedCouponTear(autoPlay = true)
}

@Preview
@Composable
private fun PerforatedCouponTearDemoPreview() {
    PerforatedCouponTearDemo()
}
