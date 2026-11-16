package com.ngocdevv.nativecook.blocks.charts.sparkspherescore

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The RN block's haptics map (touch only; autoplay and the return spring are silent): onset (light) when the finger lands on the ruler,
 * detent (selection) every 0.1x, cross (rigid) at 1.0x and 0.0x, limit (rigid) once per pull past either end, settle (light) when the cell
 * has cooled back, probe (soft) when the finger lands on the sphere, tap (rigid) on the crystal.
 */
internal class SparkSphereScoreHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): SparkSphereScoreHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return SparkSphereScoreHaptics(vibrator)
        }
    }

    private var last = 0L

    fun emit(cue: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val now = System.nanoTime()
        if (now - last < 60_000_000L) return
        last = now
        val effect = if (Build.VERSION.SDK_INT >= 29) {
            when (cue) {
                SparkSphereScoreCue.CROSS, SparkSphereScoreCue.LIMIT, SparkSphereScoreCue.TAP ->
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                SparkSphereScoreCue.DETENT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                SparkSphereScoreCue.PROBE -> VibrationEffect.createWaveform(longArrayOf(0, 12), intArrayOf(0, 35), -1)
                else -> VibrationEffect.createWaveform(longArrayOf(0, 14), intArrayOf(0, 70), -1)
            }
        } else {
            when (cue) {
                SparkSphereScoreCue.CROSS, SparkSphereScoreCue.LIMIT, SparkSphereScoreCue.TAP ->
                    VibrationEffect.createWaveform(longArrayOf(0, 35), intArrayOf(0, 200), -1)
                SparkSphereScoreCue.DETENT -> VibrationEffect.createWaveform(longArrayOf(0, 10), intArrayOf(0, 50), -1)
                SparkSphereScoreCue.PROBE -> VibrationEffect.createWaveform(longArrayOf(0, 12), intArrayOf(0, 35), -1)
                else -> VibrationEffect.createWaveform(longArrayOf(0, 14), intArrayOf(0, 70), -1)
            }
        }
        // A failed request must never stop rendering; hosts copying the block must declare android.permission.VIBRATE.
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
        }
    }
}
