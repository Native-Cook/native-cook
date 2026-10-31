package com.ngocdevv.nativecook.blocks.cards.marketbagcheckout

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/** Preloads touch-only cues; autoplay stays silent and the ringer-mode gate is preserved. */
internal class MarketBagCheckoutAudio(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val soundPool =
        SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    private val loadedSounds = mutableSetOf<Int>()
    private val activeStreams = mutableMapOf<String, Int>()
    private val soundIds: Map<String, Int>

    init {
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            if (status == 0) loadedSounds.add(soundId)
        }
        soundIds =
            listOf("lift", "drop", "cinch", "paid").associateWith { cue ->
                context.assets.openFd("marketbagcheckout/marketbagcheckout_mbc-$cue.wav").use {
                    soundPool.load(it, 1)
                }
            }
    }

    /** Restarts the cue at its authored volume when its sample and the normal ringer are ready. */
    fun play(cue: String) {
        val soundId = soundIds[cue] ?: return
        if (soundId !in loadedSounds || audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL)
            return
        val volume =
            when (cue) {
                "lift" -> 0.35f
                "drop" -> 0.6f
                "cinch" -> 0.5f
                else -> 0.6f
            }
        activeStreams[cue]?.let { soundPool.stop(it) }
        activeStreams[cue] = soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    /** Stops active streams when the screen leaves its resumed lifecycle. */
    fun stop() {
        activeStreams.values.forEach { soundPool.stop(it) }
    }

    /** Releases the sound pool when the remembered audio instance is disposed. */
    fun close() {
        soundPool.release()
    }
}
