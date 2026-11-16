package com.ngocdevv.nativecook.blocks.cards.coinpilecollect

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** The haptics map of cpc-haptics.ts. Only touches play it; autoplay is silent. */
internal class CoinPileCollectHaptics private constructor(private val vibrator: Vibrator?) {
    companion object {
        fun create(context: Context): CoinPileCollectHaptics {
            val v = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return CoinPileCollectHaptics(v)
        }
    }

    private fun play(predefined: Int, ms: Long, amp: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(predefined)
        else VibrationEffect.createWaveform(longArrayOf(0, ms), intArrayOf(0, amp), -1)
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
        }
    }

    fun press() = play(VibrationEffect.EFFECT_CLICK, 25, 120)
    fun coin() = play(VibrationEffect.EFFECT_TICK, 10, 50)
    fun click() = play(VibrationEffect.EFFECT_TICK, 8, 40)
    fun latch() = play(VibrationEffect.EFFECT_TICK, 10, 50)
    fun empty() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 12, 40, 18), intArrayOf(0, 90, 0, 160), -1))
        } catch (_: SecurityException) {
        }
    }
}

/** clink / clink2 (alternating) and thunk, played only from touch. */
internal class CoinPileCollectAudio(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        )
        .build()
    private var clink = 0
    private var clink2 = 0
    private var thunk = 0
    private var flip = 0

    init {
        try {
            context.assets.openFd("coinpilecollect/coinpilecollect_clink.wav").use { clink = pool.load(it, 1) }
            context.assets.openFd("coinpilecollect/coinpilecollect_clink2.wav").use { clink2 = pool.load(it, 1) }
            context.assets.openFd("coinpilecollect/coinpilecollect_thunk.wav").use { thunk = pool.load(it, 1) }
        } catch (_: Exception) {
        }
    }

    fun clink() {
        flip = 1 - flip
        val id = if (flip == 1) clink else clink2
        if (id != 0) pool.play(id, 0.7f, 0.7f, 1, 0, 1f)
    }

    fun thunk() {
        if (thunk != 0) pool.play(thunk, 0.8f, 0.8f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
