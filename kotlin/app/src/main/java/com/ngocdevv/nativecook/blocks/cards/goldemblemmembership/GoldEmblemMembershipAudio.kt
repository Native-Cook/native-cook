package com.ngocdevv.nativecook.blocks.cards.goldemblemmembership

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

internal class GoldEmblemMembershipAudio(context: Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(2).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>()
    private val sounds: Map<String,Int>
    private val streams=mutableMapOf<String,Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0)loaded.add(id) }
        sounds=listOf("melt","stamp").associateWith { name ->
            context.assets.openFd("goldemblemmembership/goldemblemmembership_gem-$name.wav").use { pool.load(it,1) }
        }
    }
    fun play(name: String) {
        val id=sounds[name] ?: return
        if(id !in loaded || manager?.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return
        streams[name]?.let { pool.stop(it) }
        val v=if(name=="melt")0.55f else 0.65f
        streams[name]=pool.play(id,v,v,1,0,1f)
    }
    fun close() { pool.release() }
}
