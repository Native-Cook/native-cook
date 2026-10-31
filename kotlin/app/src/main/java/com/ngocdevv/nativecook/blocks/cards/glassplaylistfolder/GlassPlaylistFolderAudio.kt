package com.ngocdevv.nativecook.blocks.cards.glassplaylistfolder

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

internal class GlassPlaylistFolderAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ready = mutableSetOf<Int>()
    private val sounds = mutableMapOf<String, Int>()
    private val streams = mutableMapOf<String, Int>()
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(ready) { ready.add(id) } }
        for (name in listOf("open", "close", "tap")) {
            context.assets.openFd("glassplaylistfolder/glassplaylistfolder_gpf-$name.wav").use {
                sounds[name] = pool.load(it, 1)
            }
        }
    }
    fun play(name: String) {
        val id = sounds[name] ?: return
        if (!synchronized(ready) { id in ready } || manager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val volume = if (name == "tap") .5f else .85f
        streams[name]?.let { pool.stop(it) }
        streams[name] = pool.play(id, volume, volume, 1, 0, 1f)
    }
    fun close() { pool.release() }
}
