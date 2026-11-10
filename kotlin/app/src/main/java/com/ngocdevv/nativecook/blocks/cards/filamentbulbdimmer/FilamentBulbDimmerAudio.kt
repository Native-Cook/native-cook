package com.ngocdevv.nativecook.blocks.cards.filamentbulbdimmer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

internal class FilamentBulbDimmerAudio(context:Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(4).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>();private val streams=mutableMapOf<String,Int>();private val sounds=mutableMapOf<String,Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status->if(status==0)loaded.add(id) }
        for(c in arrayOf("pull","release","tink")){context.assets.openFd("filamentbulbdimmer/filamentbulbdimmer_fbd-$c.wav").use { sounds[c]=pool.load(it,1) }}
    }
    fun play(c:String) {val id=sounds[c]?:return;if(id !in loaded || manager?.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return;val v=when(c){"pull"->.85f;"release"->.6f;else->.35f};streams[c]?.let{pool.stop(it)};streams[c]=pool.play(id,v,v,1,0,1f)}
    fun close(){pool.release()}
}
