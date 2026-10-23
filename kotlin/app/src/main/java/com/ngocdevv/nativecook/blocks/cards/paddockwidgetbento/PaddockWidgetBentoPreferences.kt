package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

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

/** Resolve quiet mode before the first demo frame, then observe changes while mounted. */
@Composable
internal fun rememberPaddockQuietMotion(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val accessibility = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)
    }
    var reducedMotion by remember(resolver) {
        mutableStateOf(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
    var touchExploration by remember(accessibility) {
        mutableStateOf(accessibility?.isTouchExplorationEnabled == true)
    }
    DisposableEffect(resolver, accessibility) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        val listener = AccessibilityManager.TouchExplorationStateChangeListener {
            touchExploration = it
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose {
            resolver.unregisterContentObserver(observer)
            accessibility?.removeTouchExplorationStateChangeListener(listener)
        }
    }
    return reducedMotion || touchExploration
}
