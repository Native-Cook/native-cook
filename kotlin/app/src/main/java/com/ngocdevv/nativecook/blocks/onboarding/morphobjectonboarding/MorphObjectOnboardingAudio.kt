package com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.ngocdevv.nativecook.R

/** Touch-only, overlapping cues; ringer silent mode suppresses playback. */
internal class MorphObjectOnboardingAudio(context: Context) {
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
    private val sounds: Map<String, Int>
    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) loaded.add(id)
        }

        sounds = mapOf(
            "morph" to pool.load(context, R.raw.morphobjectonboarding_morph, 1),
            "pop" to pool.load(context, R.raw.morphobjectonboarding_pop, 1),
            "tick" to pool.load(context, R.raw.morphobjectonboarding_tick, 1),
        )
    }

    fun play(cue: String) {
        if (manager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = sounds[cue] ?: return
        if (id !in loaded) return
        val volume = if (cue == "tick") 0.6f else 0.85f
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun close() {
        pool.release()
    }
}
