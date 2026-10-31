package com.ngocdevv.nativecook.blocks.cards.seasontiermedals

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

internal class SeasonTierMedalsAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val sounds: Map<String, Int>
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        sounds = listOf("slide", "land", "tick").associateWith { name ->
            context.assets.openFd("seasontiermedals/seasontiermedals_stm-$name.wav").use { pool.load(it, 1) }
        }
    }
    fun play(name: String) {
        val id = sounds[name] ?: return
        if (id !in loaded || manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        streams[name]?.let { pool.stop(it) }
        val volume = when(name) { "tick" -> 0.45f; "slide" -> 0.6f; else -> 0.85f }
        streams[name] = pool.play(id, volume, volume, 1, 0, 1f)
    }
    fun close() { pool.release() }
}
