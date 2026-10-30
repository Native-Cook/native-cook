package com.ngocdevv.nativecook.blocks.pickers.cleanairdial

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.ngocdevv.nativecook.R

/** Cues answer touch only; the autoplay clock never calls play directly. */
internal class CleanAirDialFeedback(context: Context, enabled: Boolean) {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val soundPool = if (enabled) SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build() else null
    private val ready = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val sounds: Map<String, Int>

    init {
        soundPool?.setOnLoadCompleteListener { _, sample, status ->
            if (status == 0) ready.add(sample)
        }
        sounds = mapOf(
            "tick" to R.raw.cleanairdial_cad_tick,
            "tap" to R.raw.cleanairdial_cad_tap,
            "whoosh" to R.raw.cleanairdial_cad_whoosh,
            "chime" to R.raw.cleanairdial_cad_chime,
        ).mapValues { (_, resource) -> soundPool?.load(context, resource, 1) ?: 0 }
    }

    fun play(cue: String) {
        val sample = sounds[cue] ?: return
        if (sample !in ready) return
        val volume = if (cue == "tick") 0.5f else 0.85f
        val pool = soundPool ?: return
        streams[cue]?.let { pool.stop(it) }
        streams[cue] = pool.play(sample, volume, volume, 1, 0, 1f)
    }

    fun buzz(kind: Int) {
        if (Build.VERSION.SDK_INT >= 29) {
            val effect = when (kind) {
                2 -> VibrationEffect.EFFECT_TICK
                3 -> VibrationEffect.EFFECT_DOUBLE_CLICK
                1 -> VibrationEffect.EFFECT_HEAVY_CLICK
                else -> VibrationEffect.EFFECT_CLICK
            }
            vibrator?.vibrate(VibrationEffect.createPredefined(effect))
        } else vibrator?.vibrate(VibrationEffect.createOneShot(if (kind == 1) 20 else 12, if (kind == 1) 90 else 45))
    }

    fun close() {
        soundPool?.release()
        ready.clear()
        streams.clear()
    }
}
