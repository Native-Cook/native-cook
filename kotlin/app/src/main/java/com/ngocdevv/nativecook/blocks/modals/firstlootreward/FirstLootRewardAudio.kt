package com.ngocdevv.nativecook.blocks.modals.firstlootreward

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

internal class FirstLootRewardAudio(context: Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ready=mutableSetOf<Int>()
    private val ids=mutableMapOf<String,Int>()
    private val streams=mutableMapOf<String,Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0)ready.add(id) }
        for(name in listOf("pop","open","tap")) {
            context.assets.openFd("firstlootreward/firstlootreward_flr-$name.wav").use { ids[name]=pool.load(it,1) }
        }
    }
    fun play(name: String) {
        val id=ids[name]?:return
        if(id !in ready || manager?.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return
        streams[name]?.let { pool.stop(it) }
        val v=if(name=="tap")0.5f else 0.85f
        streams[name]=pool.play(id,v,v,1,0,1f)
    }
    fun close() { pool.release() }
}
