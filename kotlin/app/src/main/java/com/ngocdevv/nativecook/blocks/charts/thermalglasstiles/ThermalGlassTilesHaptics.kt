package com.ngocdevv.nativecook.blocks.charts.thermalglasstiles

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** The RN block's cues (user gestures only; autoplay is silent): onset (light), every kilometre (selection), the white-hot peak (rigid). */
internal class ThermalGlassTilesHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): ThermalGlassTilesHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return ThermalGlassTilesHaptics(vibrator)
        }
    }

    /** One of ThermalGlassTilesEngine.BUZZ_*. */
    fun emit(kind: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= 29) {
            when (kind) {
                ThermalGlassTilesEngine.BUZZ_PEAK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                ThermalGlassTilesEngine.BUZZ_KM -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                else -> VibrationEffect.createWaveform(longArrayOf(0, 12), intArrayOf(0, 60), -1)
            }
        } else {
            when (kind) {
                ThermalGlassTilesEngine.BUZZ_PEAK -> VibrationEffect.createWaveform(longArrayOf(0, 35), intArrayOf(0, 200), -1)
                ThermalGlassTilesEngine.BUZZ_KM -> VibrationEffect.createWaveform(longArrayOf(0, 10), intArrayOf(0, 50), -1)
                else -> VibrationEffect.createWaveform(longArrayOf(0, 12), intArrayOf(0, 60), -1)
            }
        }
        // A failed request must never stop rendering; hosts copying the block must declare android.permission.VIBRATE.
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
        }
    }
}

