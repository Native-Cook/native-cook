package com.ngocdevv.nativecook.blocks.cards.cardvacuumseal

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.max
import kotlin.math.min

/**
 * The block's cues, touch only (the loop is silent): haptics and, when `sound` is on, three synthesised sounds through a [SoundPool] on the
 * media stream (the host's audio session is left alone): an air hiss loop at 0.032 whose rate follows the film's state (0.8 .. 1.4), a 70 ms
 * thump at the lock (0.12) and a 200 ms inhale when the air comes back (0.12).
 *   finger lands light, a crease forms selection, the 0.9 crease rigid, the lock medium + thump,
 *   the air returns two soft taps 60 ms apart + inhale, a finger dents the sealed film soft.
 */
internal class CardVacuumSealHaptics private constructor(
    private val vibrator: Vibrator?,
    private val pool: SoundPool?,
    private val ids: IntArray,
) {
    companion object {
        private const val AIR = 0
        private const val TUP = 1
        private const val INHALE = 2
        private const val AIR_VOLUME = 0.032f
        private const val SHOT_VOLUME = 0.12f

        fun create(context: Context, sound: Boolean): CardVacuumSealHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            var pool: SoundPool? = null
            val ids = IntArray(3)
            if (sound) {
                try {
                    val sp = SoundPool.Builder()
                        .setMaxStreams(3)
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .build()
                    val names = arrayOf("air", "tup", "inhale")
                    for (i in names.indices) {
                        context.assets.openFd("cardvacuumseal/cardvacuumseal_${names[i]}.wav").use { ids[i] = sp.load(it, 1) }
                    }
                    pool = sp
                } catch (error: Exception) {
                    pool = null
                }
            }
            return CardVacuumSealHaptics(vibrator, pool, ids)
        }
    }

    private var airStream = 0

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

    private fun shot(i: Int) {
        pool?.play(ids[i], SHOT_VOLUME, SHOT_VOLUME, 1, 0, 1f)
    }

    /** `beat` is a [CardVacuumSealBeat]. */
    fun emit(beat: Int, haptics: Boolean) {
        if (haptics) {
            when (beat) {
                CardVacuumSealBeat.ONSET -> buzz(predefined(VibrationEffect.EFFECT_TICK, 12, 40))
                CardVacuumSealBeat.DETENT -> buzz(predefined(VibrationEffect.EFFECT_TICK, 10, 40))
                CardVacuumSealBeat.RIGID -> buzz(predefined(VibrationEffect.EFFECT_CLICK, 30, 90))
                CardVacuumSealBeat.LOCK -> buzz(predefined(VibrationEffect.EFFECT_CLICK, 43, 50))
                CardVacuumSealBeat.AIR_BACK -> buzz(VibrationEffect.createWaveform(longArrayOf(0, 20, 40, 20), intArrayOf(0, 30, 0, 30), -1))
                CardVacuumSealBeat.DIMPLE -> buzz(predefined(VibrationEffect.EFFECT_TICK, 12, 30))
            }
        }
        when (beat) {
            CardVacuumSealBeat.LOCK -> shot(TUP)
            CardVacuumSealBeat.AIR_BACK -> shot(INHALE)
        }
    }

    /** The air hiss: start or retune it (rate 0.8 .. 1.4), or `null` to stop it. */
    fun air(rate: Double?) {
        val sp = pool ?: return
        if (rate == null) {
            if (airStream != 0) {
                sp.stop(airStream)
                airStream = 0
            }
            return
        }
        val r = max(0.5f, min(2f, rate.toFloat()))
        if (airStream == 0) {
            airStream = sp.play(ids[AIR], AIR_VOLUME, AIR_VOLUME, 1, -1, r)
        } else {
            sp.setRate(airStream, r)
        }
    }
}
