package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

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

/** Android has no Reduce Transparency preference; the public card accepts an explicit override. */
@Composable
internal fun referralReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver

    fun read() = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
    var reduced by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduced = read()
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
