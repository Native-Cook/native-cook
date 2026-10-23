package com.ngocdevv.nativecook.blocks.gestures.chromeprizewheel

import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun ChromePrizeWheelDemo() {
    val activity = LocalActivity.current
    val composeView = LocalView.current
    DisposableEffect(activity, composeView) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, composeView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    AndroidView(
        factory = { ChromePrizeWheelView(it, autoPlay = true) },
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview
@Composable
private fun ChromePrizeWheelDemoPreview() {
    ChromePrizeWheelDemo()
}
