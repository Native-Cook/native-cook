package com.ngocdevv.nativecook.blocks.gestures.raffledrumspin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun RaffleDrumSpin(autoPlay: Boolean = false) {
    AndroidView(
        factory = { context ->
            val activity = context.findActivity()
            val intent = activity?.intent
            val bars = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            val navigationWasVisible = activity?.window?.decorView?.let { androidx.core.view.ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.navigationBars()) } ?: true
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.navigationBars())
            val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            // Preserve decimal event marks such as 5.7 before entering the Double timeline.
            val debugTime = if (debug && intent?.hasExtra("raffleT") == true) {
                intent.getFloatExtra("raffleT", 0f).toString().toDouble().coerceIn(0.0, LOOP_D)
            } else null
            val reduced = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
            RaffleDrumSpinHostView(
                context = context,
                autoPlay = autoPlay,
                reducedMotion = reduced,
                debugTime = debugTime,
                scripted = debug && intent?.hasExtra("raffleScript") == true,
                captureId = if (debug) intent?.getStringExtra("raffleCapture") else null,
                restoreNavigationBars = { if (navigationWasVisible) bars?.show(WindowInsetsCompat.Type.navigationBars()) },
            )
        },
        modifier = Modifier.fillMaxSize(),
        update = { it.autoPlay = autoPlay },
        onRelease = { it.release() },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
