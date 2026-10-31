package com.ngocdevv.nativecook.blocks.onboarding.coinlightlanding

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

internal class CoinLightLandingAudio(context: Context) {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val pool=SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>()
    private val streams=mutableMapOf<String,Int>()
    private val sounds: Map<String,Int>
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0) loaded.add(id) }
        sounds=listOf("hum","spin","snap").associateWith { cue ->
            context.assets.openFd("coinlightlanding/coinlightlanding_cll-$cue.wav").use { pool.load(it,1) }
        }
    }
    fun play(cue: String) {
        val id=sounds[cue] ?: return
        if(id !in loaded || manager?.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return
        val v=if(cue=="hum")0.45f else 0.75f
        streams[cue]?.let { pool.stop(it) }; streams[cue]=pool.play(id,v,v,1,0,1f)
    }
    fun close() { pool.release() }
}
