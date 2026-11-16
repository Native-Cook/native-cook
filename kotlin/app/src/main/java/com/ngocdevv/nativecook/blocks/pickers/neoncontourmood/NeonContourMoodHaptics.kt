package com.ngocdevv.nativecook.blocks.pickers.neoncontourmood

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The RN block's cue map: a stop crossed is weighted by the mood (medium at the ends, soft in the middle, light elsewhere), the limit
 * and the end rest are medium, a pulse is soft, everything else light. One cue per 35 ms at most.
 */
internal class NeonContourMoodHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        private const val MIN_GAP_MS = 35L

        fun create(context: Context): NeonContourMoodHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return NeonContourMoodHaptics(vibrator)
        }
    }

    private var lastCue = 0L

    /** [cue] is one of NeonContourMoodEngine.CUE_*; [weight] 2 strong, 0 soft, else light. */
    fun emit(cue: Int, weight: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastCue < MIN_GAP_MS) return
        lastCue = now
        val medium = cue == NeonContourMoodEngine.CUE_LIMIT || (cue == NeonContourMoodEngine.CUE_DETENT && weight == 2)
        val soft = cue == NeonContourMoodEngine.CUE_PULSE || (cue == NeonContourMoodEngine.CUE_DETENT && weight == 0)
        val effect = if (Build.VERSION.SDK_INT >= 29) {
            if (medium) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            else if (soft) VibrationEffect.createWaveform(longArrayOf(0, 12), intArrayOf(0, 35), -1)
            else VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else {
            if (medium) VibrationEffect.createWaveform(longArrayOf(0, 25), intArrayOf(0, 120), -1)
            else if (soft) VibrationEffect.createWaveform(longArrayOf(0, 12), intArrayOf(0, 35), -1)
            else VibrationEffect.createWaveform(longArrayOf(0, 10), intArrayOf(0, 60), -1)
        }
        // A failed request must never stop rendering; hosts copying the block must declare android.permission.VIBRATE.
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
        }
    }
}

/** One 70 ms strike cue, a little higher for the greedier moods; finger-driven only (autoplay stays silent). */
internal class NeonContourMoodAudio(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private var id = 0

    init {
        try {
            context.assets.openFd("neoncontourmood/neoncontourmood_ncm-strike.wav").use { id = pool.load(it, 1) }
        } catch (_: Exception) {
        }
    }

    fun strike(index: Int, n: Int) {
        if (id == 0) return
        val rate = 0.9f + (1.15f - 0.9f) * (if (n > 1) index.toFloat() / (n - 1) else 0.5f)
        pool.play(id, 0.7f, 0.7f, 1, 0, rate)
    }

    fun release() = pool.release()
}

