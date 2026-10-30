package com.ngocdevv.nativecook.blocks.modals.listeningbuddypaywall

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.ngocdevv.nativecook.R

/** Five bundled cues answer touches only; ringer-muted devices remain silent. */
internal class ListeningBuddyPaywallAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ready = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val cues: Map<String, Int>
    init {
        pool.setOnLoadCompleteListener {
            _, id, status -> if (status == 0) ready.add(id)
        }
        cues = mapOf("pick1" to R.raw.listeningbuddypaywall_pick1, "pick2" to R.raw.listeningbuddypaywall_pick2, "pick3" to R.raw.listeningbuddypaywall_pick3, "boop" to R.raw.listeningbuddypaywall_boop, "notes" to R.raw.listeningbuddypaywall_notes).mapValues {
            (_, resource) -> pool.load(context, resource, 1)
        }
    }
    fun play(cue: String) {
        val id = cues.getValue(cue)
        if (id !in ready || manager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val volume = if (cue == "notes") .7f else .85f
        streams[cue]?.let { pool.stop(it) }
        streams[cue] = pool.play(id, volume, volume, 1, 0, 1f)
    }
    fun release() {
        pool.release()
    }
}
