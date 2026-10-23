package com.ngocdevv.nativecook.blocks.gestures.dayonemedal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** The gallery entry keeps the original full-screen, self-playing presentation. */
@Composable
fun DayOneMedalDemo() {
    val context = LocalContext.current
    val host = LocalView.current
    val medalView = remember(context) { DayOneMedalView(context, autoPlay = true) }

    DisposableEffect(host) {
        val activity = checkNotNull(host.context.findDayOneMedalActivity())
        val controller = WindowCompat.getInsetsController(activity.window, host)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            medalView.stop()
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    AndroidView(
        factory = { medalView },
        modifier = Modifier.fillMaxSize(),
        update = { it.start() },
    )
}
