package com.ngocdevv.nativecook.blocks.progress.vitamincapsuledose

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/** Touch-only cues. A normal ringer mode is required, matching Expo playsInSilentMode=false. */
internal class VitaminCapsuleDoseAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val sounds: Map<String, Int>
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        sounds = listOf("morph", "fill", "take", "done").associateWith { cue ->
            context.assets.openFd("vitamincapsuledose/vitamincapsuledose_vcd-$cue.wav").use { pool.load(it, 1) }
        }
    }
    fun play(cue: String, stop: Boolean = false) {
        streams.remove(cue)?.let { pool.stop(it) }
        if (stop || manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = sounds[cue] ?: return
        if (id !in loaded) return
        val volume = when (cue) { "morph" -> 0.5f; "fill" -> 0.45f; "take" -> 0.7f; else -> 0.6f }
        streams[cue] = pool.play(id, volume, volume, 1, 0, 1f)
    }
    fun close() { pool.release() }
}
