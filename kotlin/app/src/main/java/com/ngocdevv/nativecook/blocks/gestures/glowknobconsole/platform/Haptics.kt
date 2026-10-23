// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * expo-haptics' Android implementation (HapticsModule.kt + arguments/):
 * `Vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))`
 * with the exact same waveforms.
 */
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
