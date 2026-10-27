package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat

/**
 * Ember Day Review playing by itself on the whole screen, under a visible status bar with dark icons
 * (`<StatusBar style="dark" />` in React Native). `--ef previewTime <seconds>` (or `--es`) on the launch intent
 * freezes one frame of the loop for review.
 */
@Composable
fun EmberDayReviewDemo() {
    EmberDayReviewStatusBar()
    EmberDayReview(autoPlay = true, previewTime = emberDayReviewLaunchPreview())
}

/** Dark status bar icons while the block is open (it is white), and the gallery's light icons again when it closes. */
@Composable
private fun EmberDayReviewStatusBar() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val before = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = true
        onDispose { controller.isAppearanceLightStatusBars = before }
    }
}

/** The `previewTime` extra of the launch intent, as a float or a string, or null. */
@Composable
private fun emberDayReviewLaunchPreview(): Double? {
    val extras = LocalActivity.current?.intent?.extras ?: return null
    val value = extras.get("previewTime")
    if (value is Number) {
        return value.toDouble()
    }
    return (value as? String)?.toDoubleOrNull()
}

@Preview
@Composable
private fun EmberDayReviewDemoPreview() {
    EmberDayReviewDemo()
}
