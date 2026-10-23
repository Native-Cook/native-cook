package com.ngocdevv.nativecook.blocks.animations.flarenotifications.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

/**
 * expo-haptics' Android implementation (HapticsModule.kt + arguments/):
 * `Vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))`
 * with the exact same waveforms.
 */
@RequiresApi(Build.VERSION_CODES.S)
class Haptics(context: Context) {
    private val vibrator: Vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    private class Waveform(val timings: LongArray, val amplitudes: IntArray)

    private fun vibrate(type: Waveform) {
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(type.timings, type.amplitudes, -1)) }
    }

    /** Haptics.selectionAsync() */
    fun selection() = vibrate(SELECTION)

    /** Haptics.impactAsync(ImpactFeedbackStyle.*) */
    fun impact(style: Impact) = vibrate(
        when (style) {
            Impact.Light -> LIGHT
            Impact.Soft -> SOFT
            Impact.Medium -> MEDIUM
        },
    )

    enum class Impact { Light, Soft, Medium }

    private companion object {
        val SELECTION = Waveform(longArrayOf(0, 50), intArrayOf(0, 30))
        val LIGHT = Waveform(longArrayOf(0, 50), intArrayOf(0, 30))
        val SOFT = Waveform(longArrayOf(0, 50), intArrayOf(0, 30))
        val MEDIUM = Waveform(longArrayOf(0, 43), intArrayOf(0, 50))
    }
}
