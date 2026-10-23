package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** index.tsx: the demo hides the status bar and plays the autoplay loop. */
@Composable
fun TiltSwipeActionsDemo() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = findActivity(view.context)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.statusBars()) }
    }
    TiltSwipeActions(autoPlay = true)
}

private fun findActivity(context: Context): Activity? {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Preview
@Composable
private fun TiltSwipeActionsDemoPreview() {
    TiltSwipeActionsDemo()
}
