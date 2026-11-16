package com.ngocdevv.nativecook.blocks.pickers.skytimescrub

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The RN block's cue sheet (user input only; autoplay is silent): onset (medium) when the lens inflates, step (selection) every
 * 15 minutes, hour (light) every whole hour, landing (rigid) on a detent, lowerLimb (medium) and upperLimb (soft) at the horizon.
 */
internal class SkyTimeScrubHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): SkyTimeScrubHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return SkyTimeScrubHaptics(vibrator)
        }
    }

    fun emit(cue: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= 29) {
            when (cue) {
                SkyTimeScrubCue.ONSET, SkyTimeScrubCue.LOWER_LIMB -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                SkyTimeScrubCue.LANDING -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                SkyTimeScrubCue.HOUR -> VibrationEffect.createWaveform(longArrayOf(0, 14), intArrayOf(0, 70), -1)
                SkyTimeScrubCue.UPPER_LIMB -> VibrationEffect.createWaveform(longArrayOf(0, 12), intArrayOf(0, 35), -1)
                else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            }
        } else {
            when (cue) {
                SkyTimeScrubCue.ONSET, SkyTimeScrubCue.LOWER_LIMB -> VibrationEffect.createWaveform(longArrayOf(0, 25), intArrayOf(0, 120), -1)
                SkyTimeScrubCue.LANDING -> VibrationEffect.createWaveform(longArrayOf(0, 35), intArrayOf(0, 200), -1)
                SkyTimeScrubCue.HOUR -> VibrationEffect.createWaveform(longArrayOf(0, 14), intArrayOf(0, 70), -1)
                SkyTimeScrubCue.UPPER_LIMB -> VibrationEffect.createWaveform(longArrayOf(0, 12), intArrayOf(0, 35), -1)
                else -> VibrationEffect.createWaveform(longArrayOf(0, 10), intArrayOf(0, 50), -1)
            }
        }
        // A failed request must never stop rendering; hosts copying the block must declare android.permission.VIBRATE.
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
        }
    }
}

/** The four synthesised cues (assets, silent unless the block's `sound` is on): tick, night tick, detent clack, horizon swell. */
internal class SkyTimeScrubAudio(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = HashMap<String, Int>()
    private val volume = mapOf("tick" to 0.22f, "tickNight" to 0.22f, "clack" to 0.5f, "swell" to 0.4f)

    init {
        val files = mapOf("tick" to "sts-tick", "tickNight" to "sts-tick-night", "clack" to "sts-clack", "swell" to "sts-swell")
        for ((cue, file) in files) {
            try {
                context.assets.openFd("skytimescrub/skytimescrub_$file.wav").use { ids[cue] = pool.load(it, 1) }
            } catch (_: Exception) {
            }
        }
    }

    fun play(cue: String) {
        val id = ids[cue] ?: return
        val v = volume[cue] ?: 0.3f
        pool.play(id, v, v, 1, 0, 1f)
    }

    fun release() = pool.release()
}

