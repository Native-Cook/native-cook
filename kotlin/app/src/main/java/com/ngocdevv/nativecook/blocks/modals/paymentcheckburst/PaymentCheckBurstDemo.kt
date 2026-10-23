package com.ngocdevv.nativecook.blocks.modals.paymentcheckburst

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat

/** The block fills the screen, so it is shown without a stage. */
@Composable
fun PaymentCheckBurstDemo() {
    PaymentCheckBurstDemoSystemBars()
    PaymentCheckBurst(autoPlay = true)
}

/**
 * The screen is light, so both system bars get dark icons while the block is open, as React
 * Native's `StatusBar style="dark"` does. The gallery's activity uses light icons, so they are put
 * back when the block closes.
 */
@Composable
private fun PaymentCheckBurstDemoSystemBars() {
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
private fun PaymentCheckBurstDemoPreview() {
    PaymentCheckBurstDemo()
}
