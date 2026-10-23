package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun PinchToSummarizeDemo() {
    PinchToSummarize(autoPlay = true)
}

@Composable
internal fun PinchToSummarizeStatusBars(darkStatus: Boolean) {
    val view = LocalView.current
    val controller = remember(view) {
        val activity = findPinchToSummarizeActivity(view.context)
        activity?.window?.let { WindowCompat.getInsetsController(it, view) }
    }
    DisposableEffect(controller) {
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        onDispose {
            if (controller != null && previousStatus != null && previousNavigation != null) {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNavigation
            }
        }
    }
    SideEffect {
        controller?.isAppearanceLightStatusBars = darkStatus
    }
}

private fun findPinchToSummarizeActivity(context: Context): Activity? {
    var candidate = context
    while (candidate is ContextWrapper) {
        if (candidate is Activity) return candidate
        candidate = candidate.baseContext
    }
    return candidate as? Activity
}
