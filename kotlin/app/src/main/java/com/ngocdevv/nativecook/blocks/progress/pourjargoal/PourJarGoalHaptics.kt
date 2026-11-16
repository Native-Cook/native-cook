package com.ngocdevv.nativecook.blocks.progress.pourjargoal

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ngocdevv.nativecook.blocks.progress.pourjargoal.PourJarGoalMotion as M

/**
 * The RN cue sheet (`pjg-haptics.ts`): onset light, detent selection (max one per 85 ms across all cues), impact medium, success,
 * limit rigid, undo light. Only user input calls this; autoplay is silent and still.
 */
internal class PourJarGoalHaptics private constructor(private val vibrator: Vibrator?) {
    private var last = -1000L

    companion object {
        fun create(context: Context): PourJarGoalHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return PourJarGoalHaptics(vibrator)
        }
    }

    fun emit(cue: String) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val now = System.currentTimeMillis()
        if (now - last < 85) return
        last = now
        val effect = if (Build.VERSION.SDK_INT >= 29) {
            when (cue) {
                PourJarGoalCue.IMPACT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                PourJarGoalCue.LIMIT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                PourJarGoalCue.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 12, 40, 18), intArrayOf(0, 90, 0, 160), -1)
                else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
        } else {
            when (cue) {
                PourJarGoalCue.IMPACT -> VibrationEffect.createWaveform(longArrayOf(0, 25), intArrayOf(0, 120), -1)
                PourJarGoalCue.LIMIT -> VibrationEffect.createWaveform(longArrayOf(0, 35), intArrayOf(0, 200), -1)
                PourJarGoalCue.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 12, 40, 18), intArrayOf(0, 90, 0, 160), -1)
                else -> VibrationEffect.createWaveform(longArrayOf(0, 10), intArrayOf(0, 50), -1)
            }
        }
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
        }
    }
}

/** pour.wav (looping, rate follows the level) and land.wav (one shot), played only from touch. */
internal class PourJarGoalAudio(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private var pourId = 0
    private var landId = 0
    private var pourStream = 0

    init {
        try {
            context.assets.openFd("pourjargoal/pourjargoal_pour.wav").use { pourId = pool.load(it, 1) }
            context.assets.openFd("pourjargoal/pourjargoal_land.wav").use { landId = pool.load(it, 1) }
        } catch (_: Exception) {
        }
    }

    private fun rate(level: Double) = (0.8 + 0.6 * M.clamp(level)).toFloat()

    fun cue(name: String, level: Double, gain: Double) {
        val vol = min07(gain)
        when (name) {
            "pour" -> {
                if (pourStream != 0) pool.stop(pourStream)
                if (pourId != 0) pourStream = pool.play(pourId, vol, vol, 1, -1, rate(level))
            }
            "flow" -> if (pourStream != 0) {
                pool.setRate(pourStream, rate(level))
                pool.setVolume(pourStream, vol, vol)
            }
            "tail" -> if (pourStream != 0) pool.setLoop(pourStream, 0)
            "stop" -> {
                if (pourStream != 0) pool.stop(pourStream)
                pourStream = 0
            }
            "land" -> if (landId != 0) pool.play(landId, vol, vol, 1, 0, 1f)
        }
    }

    private fun min07(g: Double) = kotlin.math.min(0.7, g).toFloat()

    fun release() = pool.release()
}
