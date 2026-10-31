package com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/** Touch-only cues; autoplay is silent and the ringer switch suppresses playback. */
internal class MicCordOnboardingAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val sounds: Map<String, Int>

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) loaded.add(id)
        }
        sounds = mapOf(
            "grab" to load(context, "miccordonboarding/miccordonboarding_mco-grab.wav"),
            "notes" to load(context, "miccordonboarding/miccordonboarding_mco-notes.wav"),
            "swing" to load(context, "miccordonboarding/miccordonboarding_mco-swing.wav"),
        )
    }

    private fun load(context: Context, path: String): Int = context.assets.openFd(path).use { descriptor ->
        pool.load(descriptor, 1)
    }

    fun play(cue: String) {
        if (manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = sounds[cue] ?: return
        if (id !in loaded) return
        streams[cue]?.let(pool::stop)
        val volume = if (cue == "grab") 0.5f else 0.7f
        streams[cue] = pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun close() {
        pool.release()
    }
}
