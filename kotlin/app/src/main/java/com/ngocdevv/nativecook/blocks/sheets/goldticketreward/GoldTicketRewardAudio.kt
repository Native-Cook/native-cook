package com.ngocdevv.nativecook.blocks.sheets.goldticketreward

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.ngocdevv.nativecook.R


/** All callers are touch paths; autoplay never invokes these cues. */
internal class GoldTicketRewardAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val sounds: Map<String, Int>

    init {
        pool.setOnLoadCompleteListener {
            _, id, status -> if (status == 0)loaded.add(id)
        }
        sounds = mapOf("reveal" to pool.load(context,
            R.raw.goldticketreward_reveal,
            1),
            "coin" to pool.load(context,
            R.raw.goldticketreward_coin,
            1),
            "sparkle" to pool.load(context,
            R.raw.goldticketreward_sparkle,
            1))
    }

    fun play(cue: String) {
        val id = sounds[cue]?: return
        if (id !in loaded || manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL)return
        val volume = if (cue =="sparkle")0.7f else 0.9f
        streams[cue]?.let {
            pool.stop(it)
        }
        streams[cue] = pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun close() {
        pool.release()
    }
}
