package com.ngocdevv.nativecook.blocks.pickers.beamshapetorch

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The RN block's haptics map (no sound: a light makes none), touch only: the finger lands on the capsule (soft), a tick every 10 % of
 * brightness and every 5 deg of half-angle (selection), the lamp switching on or off (medium), the focus lock after a release
 * (rigid), the pencil landing on the ceiling (rigid), the capsule opening from or closing into the island (soft).
 */
internal class BeamShapeTorchHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): BeamShapeTorchHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return BeamShapeTorchHaptics(vibrator)
        }
    }

    /** One of [BeamShapeTorchBeat]. */
    fun emit(beat: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= 29) {
            when (beat) {
                BeamShapeTorchBeat.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                BeamShapeTorchBeat.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                BeamShapeTorchBeat.FOCUS_LOCK, BeamShapeTorchBeat.CEILING -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
        } else {
            when (beat) {
                BeamShapeTorchBeat.TICK -> VibrationEffect.createWaveform(longArrayOf(0, 12), intArrayOf(0, 40), -1)
                BeamShapeTorchBeat.CLICK -> VibrationEffect.createWaveform(longArrayOf(0, 43), intArrayOf(0, 50), -1)
                BeamShapeTorchBeat.FOCUS_LOCK, BeamShapeTorchBeat.CEILING ->
                    VibrationEffect.createWaveform(longArrayOf(0, 35), intArrayOf(0, 100), -1)
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
