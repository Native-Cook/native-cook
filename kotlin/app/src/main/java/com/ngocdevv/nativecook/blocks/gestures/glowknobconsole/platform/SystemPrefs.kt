// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.AnimationEngine

private fun readFloat(raw: String?): Float? = raw?.replace(',', '.')?.toFloatOrNull()

/**
 * Reduce Motion as React Native / Reanimated read it on Android: the
 * TRANSITION_ANIMATION_SCALE global setting is "0" (Settings › Accessibility ›
 * Remove animations sets all three animation scales to 0).
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun read() = readFloat(Settings.Global.getString(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE)) == 0f
    var reduced by remember { mutableStateOf(read()) }
    AnimationEngine.systemReduceMotion = reduced
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = read()
                AnimationEngine.systemReduceMotion = reduced
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/** Android high-contrast text (RN AccessibilityInfo.isHighTextContrastEnabled). */
@Composable
fun rememberIncreaseContrast(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val key = "high_text_contrast_enabled"
    fun read() = Settings.Secure.getInt(resolver, key, 0) != 0
    var on by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) { on = read() }
        }
        resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return on
}
