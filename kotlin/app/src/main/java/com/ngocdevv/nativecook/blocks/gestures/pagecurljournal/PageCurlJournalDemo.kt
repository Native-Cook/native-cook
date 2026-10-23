package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat

/** The book on the whole screen, turning its own pages; the status bar keeps its dark icons on the white page. */
@Composable
fun PageCurlJournalDemo() {
    PageCurlJournalStatusBar()
    PageCurlJournal(autoPlay = true)
}

/** Dark icons in the system bars while the block is open, as the gallery's light ones come back when it closes. */
@Composable
private fun PageCurlJournalStatusBar() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
}

@Preview
@Composable
private fun PageCurlJournalDemoPreview() {
    PageCurlJournalDemo()
}
