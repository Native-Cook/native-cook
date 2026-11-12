package com.ngocdevv.nativecook.blocks.cards.glasspaymorph

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** The installed Expo Haptics Android waveforms, including their exact timings and amplitudes. */
internal class GlassPayMorphHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): GlassPayMorphHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return GlassPayMorphHaptics(vibrator)
        }
    }

    /** 0 light press, 1 medium dock, 2 success pulse, 3 selection midpoint. */
    fun emit(kind: Int) {
        val timings: LongArray
        val amplitudes: IntArray
        when (kind) {
            1 -> {
                timings = longArrayOf(0, 43)
                amplitudes = intArrayOf(0, 50)
            }
            2 -> {
                timings = longArrayOf(0, 40, 100, 40)
                amplitudes = intArrayOf(0, 50, 0, 60)
            }
            else -> {
                timings = longArrayOf(0, 50)
                amplitudes = intArrayOf(0, 30)
            }
        }
        // The reference catches failed native haptic requests; rendering must keep going.
        try {
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } catch (_: SecurityException) {
            // Hosts copying the block must declare android.permission.VIBRATE.
        }
    }
}
