@file:androidx.annotation.RequiresApi(31)

package com.ngocdevv.nativecook.blocks.animations.emberchargewidget.platform

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

internal class EmberHaptics(context: Context) {
    private val vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    fun selection() {
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50), intArrayOf(0, 30), -1)) }
    }

    fun success() {
        // Match expo-haptics' concurrent Success notification and Heavy impact requests.
        runCatching {
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 40), intArrayOf(0, 50, 0, 60), -1),
            )
        }
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60), intArrayOf(0, 70), -1)) }
    }
}
