package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

internal class ReferralHaptics(
    context: Context,
) {
    @Suppress("DEPRECATION")
    private val vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
    private val selection =
        VibrationEffect.createWaveform(longArrayOf(0, 50), intArrayOf(0, 30), -1)
    private val success =
        VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 40), intArrayOf(0, 50, 0, 60), -1)

    fun play(kind: Int) {
        vibrator?.vibrate(if (kind == 2) success else selection)
    }
}
