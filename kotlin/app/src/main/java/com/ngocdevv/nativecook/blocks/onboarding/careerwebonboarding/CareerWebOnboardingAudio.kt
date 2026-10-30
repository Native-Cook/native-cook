package com.ngocdevv.nativecook.blocks.onboarding.careerwebonboarding

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.ngocdevv.nativecook.R

/** Touch-only cues; autoplay stays silent and the ringer switch is respected. */
internal class CareerWebOnboardingAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val loaded = mutableSetOf<Int>()
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
        .also { soundPool ->
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) loaded.add(sampleId)
            }
        }
    private val streams = mutableMapOf<String, Int>()
    private val sounds = mapOf(
        "pluck" to pool.load(context, R.raw.careerwebonboarding_pluck, 1),
        "attach" to pool.load(context, R.raw.careerwebonboarding_attach, 1),
        "marker" to pool.load(context, R.raw.careerwebonboarding_marker, 1),
    )

    fun play(cue: String) {
        val sample = sounds[cue] ?: return
        if (sample !in loaded || manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        streams[cue]?.let(pool::stop)
        val volume = if (cue == "marker") 0.6f else 0.85f
        streams[cue] = pool.play(sample, volume, volume, 1, 0, 1.0f)
    }

    fun close() {
        pool.release()
    }
}
