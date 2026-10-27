package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * `useMotionPreference`: the live Reduce Motion preference, which on Android is the animator
 * duration scale being 0 ("Remove animations"), including a change made while the screen is open.
 */
@Composable
internal fun rememberSpectrumNewsCarouselReducedMotion(): State<Boolean> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val reduced = remember(context) { mutableStateOf(spectrumNewsCarouselAnimationsRemoved(context)) }
    DisposableEffect(context, owner) {
        val resolver = context.contentResolver
        val settingObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced.value = spectrumNewsCarouselAnimationsRemoved(context)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            settingObserver,
        )
        // The app's own refresh when it comes back to the front (`AppState` 'active').
        val lifecycleObserver = LifecycleEventObserver { _, _ ->
            reduced.value = spectrumNewsCarouselAnimationsRemoved(context)
        }
        owner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            resolver.unregisterContentObserver(settingObserver)
            owner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return reduced
}

private fun spectrumNewsCarouselAnimationsRemoved(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
