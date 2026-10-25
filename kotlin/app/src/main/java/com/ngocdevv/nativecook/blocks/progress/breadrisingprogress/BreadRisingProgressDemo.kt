package com.ngocdevv.nativecook.blocks.progress.breadrisingprogress

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat

/**
 * The block fills the screen, edge to edge, and keeps the status bar. The light kitchen counter
 * needs dark bar icons, so the demo asks for them while it is shown.
 */
@Composable
fun BreadRisingProgressDemo() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        val previousNav = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            controller?.isAppearanceLightStatusBars = previous
            controller?.isAppearanceLightNavigationBars = previousNav
        }
    }
    BreadRisingProgress(autoPlay = true)
}

@Preview
@Composable
private fun BreadRisingProgressDemoPreview() {
    BreadRisingProgressDemo()
}
