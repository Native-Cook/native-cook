package com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The cues of the RN block (touch only; autoplay is silent), never closer than 60 ms to the previous one. onset light, release selection, part light,
 * cross selection, answer success. Two soft tones (`listen-open` when listening starts, `listen-close` when the press is released) through a [SoundPool]
 * on the media stream, without touching the host's audio session.
 */
internal class ParticleRibbonVoiceHaptics private constructor(private val vibrator: Vibrator?, private val pool: SoundPool?, private val ids: IntArray) {
    companion object {
        fun create(context: Context, sound: Boolean): ParticleRibbonVoiceHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            var pool: SoundPool? = null
            val ids = IntArray(2)
            if (sound) {
                try {
                    val sp = SoundPool.Builder()
                        .setMaxStreams(2)
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .build()
                    val names = arrayOf("listen-open", "listen-close")
                    for (i in names.indices) {
                        context.assets.openFd("particleribbonvoice/particleribbonvoice_${names[i]}.wav").use { ids[i] = sp.load(it, 1) }
                    }
                    pool = sp
                } catch (error: Exception) {
                    pool = null
                }
            }
            return ParticleRibbonVoiceHaptics(vibrator, pool, ids)
        }
    }

    private var last = -1e9

    fun release() {
        pool?.release()
    }

    private fun buzz(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        // A failed request must never stop rendering; hosts copying the block must declare android.permission.VIBRATE.
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
        }
    }

    private fun predefined(id: Int, fallbackMs: Long, amp: Int): VibrationEffect =
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(id) else VibrationEffect.createWaveform(longArrayOf(0, fallbackMs), intArrayOf(0, amp), -1)

    fun emit(beat: Int) {
        val now = System.nanoTime() / 1e6
        if (now - last < 60) return
        last = now
        when (beat) {
            ParticleRibbonVoiceBeat.ONSET, ParticleRibbonVoiceBeat.PART -> buzz(predefined(VibrationEffect.EFFECT_TICK, 12, 40))
            ParticleRibbonVoiceBeat.RELEASE, ParticleRibbonVoiceBeat.CROSS -> buzz(predefined(VibrationEffect.EFFECT_TICK, 10, 40))
            ParticleRibbonVoiceBeat.ANSWER -> buzz(VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 40), intArrayOf(0, 50, 0, 60), -1))
        }
        val sp = pool ?: return
        when (beat) {
            ParticleRibbonVoiceBeat.ONSET -> sp.play(ids[0], 1f, 1f, 1, 0, 1f)
            ParticleRibbonVoiceBeat.RELEASE -> sp.play(ids[1], 1f, 1f, 1, 0, 1f)
        }
    }
}
