package com.ngocdevv.nativecook.blocks.animations.flarenotifications.platform

import android.database.ContentObserver
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
import com.ngocdevv.nativecook.blocks.animations.flarenotifications.anim.AnimationEngine

private fun readFloat(raw: String?): Float? = raw?.replace(',', '.')?.toFloatOrNull()

/**
 * Reanimated's `useReducedMotion()` on Android: the TRANSITION_ANIMATION_SCALE
 * global setting is "0" (Settings › Accessibility › Remove animations sets all
 * three animation scales to 0). Also feeds the engine's ReduceMotion.System,
 * so un-configured animations jump to their end, as Reanimated's do.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
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
