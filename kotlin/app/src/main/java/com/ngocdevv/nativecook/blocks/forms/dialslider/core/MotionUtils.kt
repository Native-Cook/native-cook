package com.ngocdevv.nativecook.blocks.forms.dialslider.core

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Mirrors Reanimated's `ReduceMotion.System`: true when the user disabled
 * animations system-wide (animator duration scale of zero, which is what the
 * "Remove animations" accessibility setting writes).
 */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

/** Collapses an animation duration to zero when system motion is reduced. */
internal fun motionDuration(durationMillis: Int, reduceMotion: Boolean): Int =
    if (reduceMotion) 0 else durationMillis
