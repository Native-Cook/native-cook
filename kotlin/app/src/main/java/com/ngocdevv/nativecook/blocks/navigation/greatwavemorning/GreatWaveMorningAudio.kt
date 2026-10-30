package com.ngocdevv.nativecook.blocks.navigation.greatwavemorning

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.ngocdevv.nativecook.R

/** Touch cues only. Silent and vibrate modes suppress sound just as in the RN reference. */
internal class GreatWaveMorningAudio(context: Context, enabled: Boolean) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val pool = if (enabled) {
        SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    } else null
    private val loaded = mutableSetOf<Int>()
    private val ids: Map<String, Int>
    private val streams = mutableMapOf<String, Int>()
    init {
        pool?.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) loaded.add(id)
        }
        ids = if (pool != null) mapOf(
            "surge" to pool.load(context, R.raw.greatwavemorning_gwm_surge, 1),
            "splash" to pool.load(context, R.raw.greatwavemorning_gwm_splash, 1),
            "tap" to pool.load(context, R.raw.greatwavemorning_gwm_tap, 1),
        ) else emptyMap()
    }

    fun play(cue: String) {
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = ids[cue] ?: return
        if (id !in loaded) return
        streams[cue]?.let {
            pool?.stop(it)
        }
        val volume = if (cue == "surge") 0.6f else 0.85f
        pool?.play(id, volume, volume, 1, 0, 1f)?.let {
            streams[cue] = it
        }
    }

    fun stopAll() {
        streams.values.forEach { pool?.stop(it) }
        streams.clear()
    }

    fun close() {
        pool?.release()
    }
}
