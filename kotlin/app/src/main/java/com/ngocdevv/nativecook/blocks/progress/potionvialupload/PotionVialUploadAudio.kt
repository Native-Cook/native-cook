package com.ngocdevv.nativecook.blocks.progress.potionvialupload

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

internal class PotionVialUploadAudio(context:Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(5).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>()
    private val sounds=mutableMapOf<String,Int>()
    private val streams=mutableMapOf<String,Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0)synchronized(loaded){loaded.add(id)} }
        for(cue in listOf("pop","gulp","clink","chime","skitter"))context.assets.openFd("potionvialupload/potionvialupload_pvu-$cue.wav").use { sounds[cue]=pool.load(it,1) }
    }
    fun play(cue:String) {
        val id=sounds[cue]?:return
        if(!synchronized(loaded){id in loaded} || manager?.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return
        val volume=if(cue=="skitter" || cue=="clink")0.6f else 0.85f
        streams[cue]?.let{pool.stop(it)}; streams[cue]=pool.play(id,volume,volume,1,0,1f)
    }
    fun close() { pool.release() }
}
