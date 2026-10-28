package com.ngocdevv.nativecook.shared

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

/** Observe Remove animations while a block is mounted, including after Settings changes. */
@Composable
fun rememberLiveReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun readPreference() = Settings.Global.getFloat(
        resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
    ) == 0f
    var reduced by remember(resolver) { mutableStateOf(readPreference()) }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readPreference()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        reduced = readPreference()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
