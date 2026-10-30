package com.ngocdevv.nativecook.blocks.onboarding.aifeaturetour

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.ngocdevv.nativecook.R

/** Only touch events call this; autoplay remains silent. */
internal class AiFeatureTourAudio(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val loaded = mutableSetOf<Int>()
    private val streams = mutableMapOf<String, Int>()
    private val ids: Map<String, Int>
    init {
        pool.setOnLoadCompleteListener { _, sample, status -> if (status == 0) loaded.add(sample) }
        ids = mapOf(
            "tick" to pool.load(context, R.raw.aifeaturetour_aft_tick, 1),
            "whoosh" to pool.load(context, R.raw.aifeaturetour_aft_whoosh, 1),
            "sparkle" to pool.load(context, R.raw.aifeaturetour_aft_sparkle, 1),
            "tap" to pool.load(context, R.raw.aifeaturetour_aft_tap, 1),
        )
    }
    fun play(cue: String, reduced: Boolean) {
        if (reduced) return
        val id = ids.getValue(cue)
        if (id !in loaded) return
        val volume = if (cue == "tick") 0.5f else 0.85f
        // RN owns one player per cue and seeks it to zero on each activation.
        streams[cue]?.let { pool.stop(it) }
        streams[cue] = pool.play(id, volume, volume, 1, 0, 1f)
    }
    fun release() { pool.release() }
}
