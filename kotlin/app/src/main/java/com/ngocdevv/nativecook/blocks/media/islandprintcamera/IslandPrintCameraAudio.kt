package com.ngocdevv.nativecook.blocks.media.islandprintcamera

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.SoundPool

internal class IslandPrintCameraAudio(assets: AssetManager) {
    private val pool=SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val cues=mutableMapOf<String,Int>()
    private val ready=mutableSetOf<Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0)ready.add(id) }
        for(cue in listOf("shutter","motor","land"))assets.openFd("islandprintcamera/islandprintcamera_$cue.wav").use { cues[cue]=pool.load(it,1) }
    }
    fun play(cue: String) { val id=cues[cue]?:return; if(id !in ready)return; val volume=if(cue=="motor").4f else if(cue=="land").5f else .7f; pool.play(id,volume,volume,1,0,1f) }
    fun close() { pool.release() }
}
