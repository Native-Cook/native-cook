package com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.ngocdevv.nativecook.R

/** Finger cues only; ringer silent mode and autoplay remain silent. */
internal class BlacklightFlightPassportAudio(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val loaded = mutableSetOf<Int>()
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val cues: Map<String, Int>
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        cues = mapOf(
            "on" to pool.load(context, R.raw.blacklightflightpassport_bfp_uv_on, 1),
            "off" to pool.load(context, R.raw.blacklightflightpassport_bfp_uv_off, 1),
            "route" to pool.load(context, R.raw.blacklightflightpassport_bfp_route, 1),
        )
    }
    fun play(cue: String) {
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = cues[cue] ?: return
        if (id !in loaded) return
        val volume = if (cue == "route") 0.6f else 0.85f
        pool.play(id, volume, volume, 1, 0, 1f)
    }
    fun release() { pool.release() }
}
