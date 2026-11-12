package com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Exact installed Expo Haptics Android waveforms; the caller schedules success 120 ms after burst. */
internal class PinataSmashRewardHaptics private constructor(private val vibrator: Vibrator?) {
    fun emit(kind: Int) {
        when (kind) {
            1 -> vibrate(longArrayOf(0, 50), intArrayOf(0, 30))
            2 -> vibrate(longArrayOf(0, 43), intArrayOf(0, 50))
            3 -> vibrate(longArrayOf(0, 60), intArrayOf(0, 70))
            4 -> vibrate(longArrayOf(0, 40, 120, 60), intArrayOf(0, 40, 0, 60))
        }
    }
    fun emitSuccess() = vibrate(longArrayOf(0, 40, 100, 40), intArrayOf(0, 50, 0, 60))
    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        try { vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1)) } catch (_: SecurityException) {
            // A host copying the block must declare android.permission.VIBRATE.
        }
    }
    companion object {
        fun create(context: Context): PinataSmashRewardHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            }
            return PinataSmashRewardHaptics(vibrator)
        }
    }
}
