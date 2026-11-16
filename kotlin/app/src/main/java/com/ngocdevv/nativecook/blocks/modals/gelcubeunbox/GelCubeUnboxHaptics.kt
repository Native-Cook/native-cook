package com.ngocdevv.nativecook.blocks.modals.gelcubeunbox

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The block's cues, touch only (the loop is silent): haptics and, when `sound` is on, two micro sounds through a [SoundPool] on the media stream
 * (the host's audio session is left alone): a hairline air leak at the seal and the contact when the lid closes.
 *   grab soft, seal selection + air, threshold light, close medium + contact.
 */
internal class GelCubeUnboxHaptics private constructor(
    private val vibrator: Vibrator?,
    private val pool: SoundPool?,
    private val ids: IntArray,
) {
    companion object {
        fun create(context: Context, sound: Boolean): GelCubeUnboxHaptics {
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
                    val names = arrayOf("air", "contact")
                    for (i in names.indices) {
                        context.assets.openFd("gelcubeunbox/gelcubeunbox_${names[i]}.wav").use { ids[i] = sp.load(it, 1) }
                    }
                    pool = sp
                } catch (error: Exception) {
                    pool = null
                }
            }
            return GelCubeUnboxHaptics(vibrator, pool, ids)
        }
    }

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

    /** `beat` is a [GelCubeUnboxBeat]. */
    fun emit(beat: Int, haptics: Boolean) {
        if (haptics) {
            when (beat) {
                GelCubeUnboxBeat.GRAB -> buzz(predefined(VibrationEffect.EFFECT_TICK, 12, 30))
                GelCubeUnboxBeat.SEAL -> buzz(predefined(VibrationEffect.EFFECT_TICK, 10, 40))
                GelCubeUnboxBeat.THRESHOLD -> buzz(predefined(VibrationEffect.EFFECT_TICK, 14, 50))
                GelCubeUnboxBeat.CLOSE -> buzz(predefined(VibrationEffect.EFFECT_CLICK, 30, 70))
            }
        }
        val sp = pool ?: return
        when (beat) {
            GelCubeUnboxBeat.SEAL -> sp.play(ids[0], 1f, 1f, 1, 0, 1f)
            GelCubeUnboxBeat.CLOSE -> sp.play(ids[1], 1f, 1f, 1, 0, 1f)
        }
    }
}
