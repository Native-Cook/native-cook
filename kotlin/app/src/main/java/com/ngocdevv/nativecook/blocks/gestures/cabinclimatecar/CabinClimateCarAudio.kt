package com.ngocdevv.nativecook.blocks.gestures.cabinclimatecar

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import android.view.View

internal class CabinClimateCarAudio(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val sounds = mutableMapOf<String,Int>()
    private val streams = mutableMapOf<String,Int>()
    private val loaded = mutableSetOf<Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if (status == 0) loaded.add(id) }
        for (cue in listOf("tick","slide","air","tap")) {
            val id = context.resources.getIdentifier("cabinclimatecar_ccc_$cue","raw",context.packageName)
            if (id != 0) sounds[cue] = pool.load(context,id,1)
        }
    }
    fun play(cue: String,reduced: Boolean,view: View) {
        if (!reduced) {
            sounds[cue]?.takeIf { it in loaded }?.let { sound ->
                streams[cue]?.let(pool::stop)
                val volume = if (cue == "tick") 0.5f else 0.85f
                streams[cue] = pool.play(sound,volume,volume,1,0,1f)
            }
        }
        view.performHapticFeedback(if (cue == "tick" || cue == "air")
            HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.VIRTUAL_KEY)
    }
    fun stop() { streams.values.forEach(pool::stop); streams.clear() }
    fun close() { pool.release() }
}
