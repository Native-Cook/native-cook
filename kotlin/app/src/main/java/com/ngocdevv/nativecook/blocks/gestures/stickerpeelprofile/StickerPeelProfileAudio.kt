package com.ngocdevv.nativecook.blocks.gestures.stickerpeelprofile

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

internal class StickerPeelProfileAudio(context:Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>()
    private val sounds=mutableMapOf<String,Int>()
    private val streams=mutableMapOf<String,Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0)loaded.add(id) }
        for(name in listOf("peel","slap","tap")) context.assets.openFd("stickerpeelprofile/stickerpeelprofile_spp-$name.wav").use { sounds[name]=pool.load(it,1) }
    }
    fun play(name:String) {
        val id=sounds[name]?:return
        if(id !in loaded || manager?.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return
        streams[name]?.let { pool.stop(it) }; val v=if(name=="tap").5f else .75f
        streams[name]=pool.play(id,v,v,1,0,1f)
    }
    fun close() { pool.release() }
}
