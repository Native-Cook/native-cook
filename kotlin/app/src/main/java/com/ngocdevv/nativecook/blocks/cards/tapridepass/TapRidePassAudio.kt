package com.ngocdevv.nativecook.blocks.cards.tapridepass

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/**
 * Preloads the four bundled feedback cues and owns their SoundPool streams. Cue volumes are
 * normalized; the host suppresses playback for reduced motion.
 */
internal class TapRidePassAudio(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val soundPool =
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    private val loadedSounds = mutableSetOf<Int>()
    private val activeStreams = mutableMapOf<String, Int>()
    private val sounds: Map<String, Int>

    init {
        soundPool.setOnLoadCompleteListener { _, soundId, loadStatus ->
            if (loadStatus == 0) loadedSounds.add(soundId)
        }
        sounds =
            listOf("tick", "chime", "print", "close").associateWith { name ->
                context.assets.openFd("tapridepass/tapridepass_trp-$name.wav").use {
                    soundPool.load(it, 1)
                }
            }
    }

    /** Restarts a loaded cue only while the device is in normal ringer mode. */
    fun play(name: String) {
        val soundId = sounds[name] ?: return
        if (soundId !in loadedSounds || audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL)
            return
        activeStreams[name]?.let { soundPool.stop(it) }
        val volume =
            when (name) {
                "tick" -> 0.35f
                "chime" -> 0.7f
                "print" -> 0.45f
                else -> 0.5f
            }
        activeStreams[name] = soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    /** Releases sound resources when the composable leaves composition. */
    fun close() {
        soundPool.release()
    }
}
