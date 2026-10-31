package com.ngocdevv.nativecook.blocks.onboarding.wavyframeauction

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

internal class WavyFrameAuctionAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = mutableSetOf<Int>()
    private val ids = mutableMapOf<String, Int>()
    private val streams = mutableMapOf<String, Int>()
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        for (cue in listOf("unfold", "fold", "tick")) {
            context.assets.openFd("wavyframeauction/wavyframeauction_wfa-$cue.wav").use { ids[cue] = pool.load(it, 1) }
        }
    }
    fun play(cue: String) {
        val id = ids[cue] ?: return
        if (id !in loaded || manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        streams[cue]?.let(pool::stop)
        val volume = if (cue == "tick") 0.45f else 0.7f
        streams[cue] = pool.play(id, volume, volume, 1, 0, 1f)
    }
    fun close() { pool.release() }
}
