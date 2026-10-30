package com.ngocdevv.nativecook.blocks.progress.setupchecklistallset

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.ngocdevv.nativecook.R

/** Only touch calls play; the autoplay timeline remains silent. */
internal class SetupChecklistAllSetAudio(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableMapOf<String,Int>()
    private val ids: Map<String,Int>
    init {
        pool.setOnLoadCompleteListener { _,sample,status -> if (status == 0) loaded.add(sample) }
        ids = mapOf(
            "check" to pool.load(context,R.raw.setupchecklistallset_sca_check,1),
            "uncheck" to pool.load(context,R.raw.setupchecklistallset_sca_uncheck,1),
            "complete" to pool.load(context,R.raw.setupchecklistallset_sca_complete,1),
            "pop" to pool.load(context,R.raw.setupchecklistallset_sca_pop,1))
    }
    fun play(cue: String) {
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = ids[cue] ?: return
        if (id !in loaded) return
        val volume = if (cue == "complete") 0.7f else 0.85f
        streams[cue]?.let { pool.stop(it) }
        streams[cue] = pool.play(id,volume,volume,1,0,1f)
    }
    fun close() { pool.release(); loaded.clear(); streams.clear() }
}
