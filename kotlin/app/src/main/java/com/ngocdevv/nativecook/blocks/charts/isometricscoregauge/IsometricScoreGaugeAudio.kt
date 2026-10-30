package com.ngocdevv.nativecook.blocks.charts.isometricscoregauge

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.ngocdevv.nativecook.R

/** Touch-only, mixed sound effects. The SoundPool shares the component's visible lifecycle. */
internal class IsometricScoreGaugeAudio(context: Context) : AutoCloseable {
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val sounds: Map<String, Int>
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        sounds = mapOf(
            "roll" to pool.load(context, R.raw.isometricscoregauge_roll, 1),
            "pick" to pool.load(context, R.raw.isometricscoregauge_pick, 1),
            "up" to pool.load(context, R.raw.isometricscoregauge_up, 1),
        )
    }
    fun play(cue: String) {
        val id = sounds[cue] ?: return
        if (id !in loaded) return
        val volume = if (cue == "roll") 0.6f else 0.85f
        streams.remove(cue)?.let { pool.stop(it) }
        streams[cue] = pool.play(id, volume, volume, 1, 0, 1f)
    }
    override fun close() { pool.release(); loaded.clear(); streams.clear() }
}
