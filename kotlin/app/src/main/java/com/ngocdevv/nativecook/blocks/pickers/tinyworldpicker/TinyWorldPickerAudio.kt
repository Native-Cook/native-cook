package com.ngocdevv.nativecook.blocks.pickers.tinyworldpicker

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

internal class TinyWorldPickerAudio(context: Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>()
    private val sounds=mutableListOf<Int>()
    private val streams=IntArray(3)
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0)loaded.add(id) }
        for(name in listOf("morph","commit","tap"))context.assets.openFd("tinyworldpicker/tinyworldpicker_twp-$name.wav").use { sounds.add(pool.load(it,1)) }
    }
    fun play(i: Int) {
        if(i !in sounds.indices || sounds[i] !in loaded || manager?.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return
        val volume=if(i==0)0.45f else 0.6f
        pool.stop(streams[i]); streams[i]=pool.play(sounds[i],volume,volume,1,0,1f)
    }
    fun close() {pool.release()}
}
