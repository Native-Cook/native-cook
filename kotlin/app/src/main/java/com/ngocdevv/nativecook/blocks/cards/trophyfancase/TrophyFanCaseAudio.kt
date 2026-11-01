package com.ngocdevv.nativecook.blocks.cards.trophyfancase

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/** Touch-only cues. Auto-play and reduced motion never call this player. */
internal class TrophyFanCaseAudio(
    context: Context,
    private val enabled: Boolean,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val sounds = mutableMapOf<String, Int>()

    init {
        if (enabled) {
            pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded.add(id) } }
            listOf("fan", "flip", "lift", "tick").forEach { name ->
                sounds[name] = runCatching {
                    context.assets.openFd("trophyfancase/trophyfancase_tfc-$name.wav").use { pool.load(it, 1) }
                }.getOrDefault(0)
            }
        }
    }

    fun play(cue: String) {
        if (!enabled || audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = sounds[cue] ?: return
        if (id == 0 || !synchronized(loaded) { id in loaded }) return
        val volume = if (cue == "tick") 0.45f else 0.75f
        streams[cue]?.let { pool.stop(it) }
        streams[cue] = pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun close() {
        pool.release()
    }
}

