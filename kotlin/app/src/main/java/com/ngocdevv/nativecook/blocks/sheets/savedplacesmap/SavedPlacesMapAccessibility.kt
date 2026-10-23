package com.ngocdevv.nativecook.blocks.sheets.savedplacesmap

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Reduce Motion and the screen reader, followed live (both can be switched while the screen is open). */
internal class LiveAccessibility(
    val reduceMotion: Boolean,
    val screenReader: Boolean,
    val serviceEnabled: Boolean,
) {
    /** Either one: the loop settles to the pin's frame and stops. Touch and the accessibility actions still work. */
    val calm: Boolean get() = reduceMotion || screenReader
}

private fun readReduceMotion(context: Context): Boolean {
    return Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

@Composable
internal fun rememberLiveAccessibility(): LiveAccessibility {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var reduceMotion by remember { mutableStateOf(readReduceMotion(context)) }
    var screenReader by remember { mutableStateOf(manager.isTouchExplorationEnabled) }
    var serviceEnabled by remember { mutableStateOf(manager.isEnabled) }
    DisposableEffect(context, manager) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = readReduceMotion(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        val touchListener = AccessibilityManager.TouchExplorationStateChangeListener { enabled -> screenReader = enabled }
        val stateListener = AccessibilityManager.AccessibilityStateChangeListener { enabled -> serviceEnabled = enabled }
        manager.addTouchExplorationStateChangeListener(touchListener)
        manager.addAccessibilityStateChangeListener(stateListener)
        // Values can change between the first read and the registration.
        reduceMotion = readReduceMotion(context)
        screenReader = manager.isTouchExplorationEnabled
        serviceEnabled = manager.isEnabled
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            manager.removeTouchExplorationStateChangeListener(touchListener)
            manager.removeAccessibilityStateChangeListener(stateListener)
        }
    }
    return LiveAccessibility(reduceMotion, screenReader, serviceEnabled)
}
