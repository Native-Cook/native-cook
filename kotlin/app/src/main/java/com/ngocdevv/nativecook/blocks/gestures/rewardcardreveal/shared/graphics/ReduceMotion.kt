package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Mirrors Reanimated's `useReducedMotion()`: Android reports "remove animations" by forcing the
 * animator duration scale to zero.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        scale == 0f
    }
}
