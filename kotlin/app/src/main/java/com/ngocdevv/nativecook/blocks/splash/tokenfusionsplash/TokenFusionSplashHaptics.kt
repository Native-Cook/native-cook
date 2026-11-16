package com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The RN block's haptic cues (never for autoplay; at most one every 60 ms): 1 touch-down light, 2 the neck snaps at a gap of 110 pt
 * soft, 3 capture below 1500 pt/s medium, 4 capture above it heavy, 5 the brand lands after a user-initiated flood success.
 */
internal class TokenFusionSplashHaptics private constructor(private val vibrator: Vibrator?) {
    private var last = Long.MIN_VALUE / 2

    companion object {
        fun create(context: Context): TokenFusionSplashHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return TokenFusionSplashHaptics(vibrator)
        }
    }

    fun emit(cue: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val now = System.currentTimeMillis()
        if (now - last < 60) return
        last = now
        val effect = if (Build.VERSION.SDK_INT >= 29) {
            when (cue) {
                TokenFusionSplashCue.TOUCH -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                TokenFusionSplashCue.SNAP -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                TokenFusionSplashCue.CAPTURE_FAST -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                TokenFusionSplashCue.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 40), intArrayOf(0, 50, 0, 60), -1)
                else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            }
        } else {
            when (cue) {
                TokenFusionSplashCue.TOUCH, TokenFusionSplashCue.SNAP -> VibrationEffect.createWaveform(longArrayOf(0, 12), intArrayOf(0, 40), -1)
                TokenFusionSplashCue.CAPTURE_FAST -> VibrationEffect.createWaveform(longArrayOf(0, 60), intArrayOf(0, 100), -1)
                TokenFusionSplashCue.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 40), intArrayOf(0, 50, 0, 60), -1)
                else -> VibrationEffect.createWaveform(longArrayOf(0, 43), intArrayOf(0, 50), -1)
            }
        }
        // A failed request must never stop rendering; hosts copying the block must declare android.permission.VIBRATE.
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
        }
    }
}
