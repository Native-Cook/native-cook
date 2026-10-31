package com.ngocdevv.nativecook.blocks.inputs.clayprompt

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/** Only manual interactions sound; Android's silent/vibrate modes are respected. */
internal class ClayPromptAudio(context:Context) {
    private val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val pool=SoundPool.Builder().setMaxStreams(3).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ready=mutableSetOf<Int>()
    private val sounds:Map<String,Int>
    init {pool.setOnLoadCompleteListener{_,id,status->if(status==0)synchronized(ready){ready.add(id)}}
        sounds=listOf("pop","melt","bloom").associateWith{cue->context.assets.openFd("clayprompt/clayprompt_cpr-$cue.wav").use{pool.load(it,1)}}}
    fun play(cue:String){val id=sounds[cue]?:return;if(manager.ringerMode!=AudioManager.RINGER_MODE_NORMAL || !synchronized(ready){id in ready})return;val volume=if(cue=="pop")0.6f else 0.7f;pool.play(id,volume,volume,1,0,1f)}
    fun close(){pool.release()}
}
