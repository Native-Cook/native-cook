/** Short bundled sound cues used by the finger-driven delete and Undo transitions. */
package com.ngocdevv.nativecook.blocks.gestures.crumpletodelete

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/** Short cues play only from finger-driven paths; autoplay remains silent. */
internal class CrumpleToDeleteAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool =
        SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val sounds: Map<String, Int>

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        sounds =
            listOf("rustle", "toss", "bin", "pop", "open").associateWith { cue ->
                context.assets.openFd("crumpletodelete/crumpletodelete_ctd-$cue.wav").use {
                    pool.load(it, 1)
                }
            }
    }

    /** Plays a loaded cue when the device ringer permits sound. */
    fun play(cue: String) {
        val id = sounds[cue] ?: return
        if (id !in loaded || manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val volume =
            when (cue) {
                "toss" -> 0.4f
                "bin" -> 0.8f
                else -> 0.5f
            }
        streams[cue]?.let { pool.stop(it) }
        streams[cue] = pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun close() = pool.release()
}
