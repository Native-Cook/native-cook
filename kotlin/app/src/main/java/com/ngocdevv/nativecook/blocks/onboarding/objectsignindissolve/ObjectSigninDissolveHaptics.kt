package com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The RN block's haptics map, touch only: finger lands on the pear (light), a scan line per 25 pt of plane travel (selection tick),
 * the skin closing at the stalk (rigid), the spin dissolve crossing half-way (soft), the theme toggle and the buttons (light).
 */
internal class ObjectSigninDissolveHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): ObjectSigninDissolveHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return ObjectSigninDissolveHaptics(vibrator)
        }
    }

    /** One of [ObjectSigninDissolveBeat]. */
    fun emit(beat: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= 29) {
            when (beat) {
                ObjectSigninDissolveBeat.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                ObjectSigninDissolveBeat.CLOSE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                ObjectSigninDissolveBeat.SPIN -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            }
        } else {
            when (beat) {
                ObjectSigninDissolveBeat.TICK, ObjectSigninDissolveBeat.SPIN ->
                    VibrationEffect.createWaveform(longArrayOf(0, 12), intArrayOf(0, 40), -1)
                ObjectSigninDissolveBeat.CLOSE -> VibrationEffect.createWaveform(longArrayOf(0, 35), intArrayOf(0, 100), -1)
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
