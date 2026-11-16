package com.ngocdevv.nativecook.blocks.media.extrusionrecapstory

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** The RN block's cues (user input only; autoplay is silent): a grab (light), crossing the wall (medium), a landing (light). */
internal class ExtrusionRecapStoryHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): ExtrusionRecapStoryHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return ExtrusionRecapStoryHaptics(vibrator)
        }
    }

    /** One of ExtrusionRecapStoryEngine.CUE_*. */
    fun emit(cue: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val medium = cue == ExtrusionRecapStoryEngine.CUE_WALL
        val effect = if (Build.VERSION.SDK_INT >= 29) {
            if (medium) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK) else VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else {
            if (medium) VibrationEffect.createWaveform(longArrayOf(0, 25), intArrayOf(0, 120), -1)
            else VibrationEffect.createWaveform(longArrayOf(0, 12), intArrayOf(0, 60), -1)
        }
        // A failed request must never stop rendering; hosts copying the block must declare android.permission.VIBRATE.
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
        }
    }
}
