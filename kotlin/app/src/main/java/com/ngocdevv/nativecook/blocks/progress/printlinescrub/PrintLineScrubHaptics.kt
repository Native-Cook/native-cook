package com.ngocdevv.nativecook.blocks.progress.printlinescrub

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The RN block's haptics map (touch only; autoplay is silent): the finger lands on the capsule (soft, the servo wakes), the line
 * crosses every 10th layer (selection, at most every 60 ms), the line hits either end of its range (rigid, once per contact), the
 * line is back on the live layer (success, the dots flash).
 */
internal class PrintLineScrubHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): PrintLineScrubHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return PrintLineScrubHaptics(vibrator)
        }
    }

    /** One of [PrintLineScrubBeat]. */
    fun emit(beat: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= 29) {
            when (beat) {
                PrintLineScrubBeat.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                PrintLineScrubBeat.LIMIT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                PrintLineScrubBeat.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 40), intArrayOf(0, 50, 0, 60), -1)
                else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
        } else {
            when (beat) {
                PrintLineScrubBeat.TICK -> VibrationEffect.createWaveform(longArrayOf(0, 12), intArrayOf(0, 40), -1)
                PrintLineScrubBeat.LIMIT -> VibrationEffect.createWaveform(longArrayOf(0, 35), intArrayOf(0, 100), -1)
                PrintLineScrubBeat.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 40), intArrayOf(0, 50, 0, 60), -1)
                else -> VibrationEffect.createWaveform(longArrayOf(0, 50), intArrayOf(0, 30), -1)
            }
        }
        // A failed request must never stop rendering; hosts copying the block must declare android.permission.VIBRATE.
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
        }
    }
}
