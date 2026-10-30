package com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import android.view.View
import com.ngocdevv.nativecook.R


/** Autoplay never calls feedback. Respect Android's ringer and the system haptic preference. */
internal class GhostFloatWelcomeFeedback(private val context: Context, private val view: View) {
    private val pool = SoundPool.Builder().setMaxStreams(3)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ready = mutableSetOf<Int>()
    private val cues: Map<String, Int>
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready.add(id) }
        cues = mapOf(
            "boo" to pool.load(context, R.raw.ghostfloatwelcome_boo, 1),
            "tick" to pool.load(context, R.raw.ghostfloatwelcome_tick, 1),
            "swish" to pool.load(context, R.raw.ghostfloatwelcome_swish, 1),
        )
    }

    fun cue(name: String) {
        val audio = requireNotNull(context.getSystemService(AudioManager::class.java))
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = cues[name] ?: return
        if (id !in ready) return
        val volume = if (name == "swish") 0.55f else 0.85f
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun buzz(kind: Int) {
        val effect = when (kind) {
            2 -> HapticFeedbackConstants.CLOCK_TICK
            3 -> HapticFeedbackConstants.LONG_PRESS
            1 -> HapticFeedbackConstants.CONTEXT_CLICK
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(effect)
    }

    fun release() { pool.release() }
}
