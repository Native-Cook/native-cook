package com.ngocdevv.nativecook.blocks.charts.savingsfuturesim

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.ngocdevv.nativecook.R

/** Only touch triggers samples; autoplay is silent. */
internal class SavingsFutureSimAudio(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val ready = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val sounds: Map<String, Int>
    init {
        pool.setOnLoadCompleteListener { _, sample, status -> if (status == 0) ready.add(sample) }
        sounds = mapOf(
            "unfurl" to R.raw.savingsfuturesim_sfs_unfurl,
            "furl" to R.raw.savingsfuturesim_sfs_furl,
            "tick" to R.raw.savingsfuturesim_sfs_tick,
            "tap" to R.raw.savingsfuturesim_sfs_tap
        ).mapValues { (_, resource) -> pool.load(context,resource,1) }
    }
    fun play(cue: String) {
        val id = sounds[cue] ?: return
        if (id !in ready) return
        val volume = if (cue == "tick") 0.5f else 0.85f
        streams[cue]?.let { pool.stop(it) }
        streams[cue] = pool.play(id,volume,volume,1,0,1f)
    }
    fun release() { pool.release() }
}
