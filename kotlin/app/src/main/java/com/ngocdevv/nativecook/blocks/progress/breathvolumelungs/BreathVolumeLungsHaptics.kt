package com.ngocdevv.nativecook.blocks.progress.breathvolumelungs

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

internal object BreathVolumeLungsCue {
    const val ONSET = 0
    const val LITRE = 1
    const val RESULT = 2
}

/** The RN block's haptics map: onset (light), litre (medium), result (success). Touch only; at most one cue per 100 ms. */
internal class BreathVolumeLungsHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): BreathVolumeLungsHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return BreathVolumeLungsHaptics(vibrator)
        }
    }

    private var last = 0L

    fun emit(cue: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val now = System.nanoTime()
        if (now - last < 100_000_000L) return
        last = now
        val effect = when (cue) {
            BreathVolumeLungsCue.RESULT ->
                if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                else VibrationEffect.createWaveform(longArrayOf(0, 20, 60, 30), intArrayOf(0, 120, 0, 200), -1)
            BreathVolumeLungsCue.LITRE ->
                if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                else VibrationEffect.createWaveform(longArrayOf(0, 25), intArrayOf(0, 150), -1)
            else -> VibrationEffect.createWaveform(longArrayOf(0, 14), intArrayOf(0, 70), -1)
        }
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
        }
    }
}
