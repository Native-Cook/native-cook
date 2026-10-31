package com.ngocdevv.nativecook.blocks.onboarding.swipetoorderonboarding

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

internal class SwipeToOrderOnboardingAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ready = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val sounds: Map<String, Int>
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready.add(id) }
        sounds = listOf("snap", "done", "back").associateWith { cue ->
            context.assets.openFd("swipetoorderonboarding/swipetoorderonboarding_sto-$cue.wav").use { pool.load(it, 1) }
        }
    }
    fun play(cue: String) {
        val id = sounds[cue] ?: return
        if (id !in ready || manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        streams[cue]?.let { pool.stop(it) }
        val v = if (cue == "snap") 0.55f else 0.75f
        streams[cue] = pool.play(id, v, v, 1, 0, 1f)
    }
    fun close() { pool.release() }
}
