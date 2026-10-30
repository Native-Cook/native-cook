package com.ngocdevv.nativecook.blocks.cards.sunuvbuddy

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.ngocdevv.nativecook.R

/** Original short cues answer touch only; the system ringer mode gates playback. */
internal class SunUvBuddyAudio(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val loaded = mutableSetOf<Int>()
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build().apply { setOnLoadCompleteListener { _, sample, status -> if (status == 0) loaded.add(sample) } }
    private val samples = mapOf(
        "boing" to pool.load(context, R.raw.sunuvbuddy_suv_boing, 1),
        "tick" to pool.load(context, R.raw.sunuvbuddy_suv_tick, 1),
        "shades" to pool.load(context, R.raw.sunuvbuddy_suv_shades, 1),
    )
    fun play(cue: String) {
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val sample = samples[cue] ?: return
        if (sample !in loaded) return
        val volume = if (cue == "tick") 0.5f else 0.85f
        pool.play(sample, volume, volume, 1, 0, 1f)
    }
    fun release() { pool.release() }
}
